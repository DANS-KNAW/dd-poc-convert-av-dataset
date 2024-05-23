/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.avconvert.core;

import nl.knaw.dans.bagit.creator.CreatePayloadManifestsVistor;
import nl.knaw.dans.bagit.creator.CreateTagManifestsVistor;
import nl.knaw.dans.bagit.domain.Bag;
import nl.knaw.dans.bagit.domain.Manifest;
import nl.knaw.dans.bagit.exceptions.InvalidBagitFileFormatException;
import nl.knaw.dans.bagit.exceptions.MaliciousPathException;
import nl.knaw.dans.bagit.exceptions.UnparsableVersionException;
import nl.knaw.dans.bagit.exceptions.UnsupportedAlgorithmException;
import nl.knaw.dans.bagit.reader.BagReader;
import nl.knaw.dans.bagit.util.PathUtils;
import nl.knaw.dans.bagit.writer.ManifestWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nl.knaw.dans.bagit.hash.Hasher.createManifestToMessageDigestMap;
import static nl.knaw.dans.bagit.util.PathUtils.getDataDir;

public class ManifestsUpdater {

    private final Charset fileEncoding;
    private final Bag bag;
    private final Path rootDir;
    private final Path bagitDir;

    public ManifestsUpdater(Path bagDir) throws MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException, InvalidBagitFileFormatException, IOException {
        bag = new BagReader().read(bagDir);
        fileEncoding = bag.getFileEncoding();
        rootDir = bag.getRootDir();
        bagitDir = PathUtils.getBagitDir(bag);

    }

    public void updateAll()
        throws NoSuchAlgorithmException, IOException {

        // TODO Do the datasets have other big files?
        //  Then override visitFile and reuse values for paths not in fileIdToBagLocationMap.values().
        var payLoadManifests = bag.getPayLoadManifests();
        modifyPayloads(payLoadManifests);
        ManifestWriter.writePayloadManifests(payLoadManifests, bagitDir, rootDir, fileEncoding);

        var tagManifests = bag.getTagManifests();
        var tagFilesMap = getManifestToDigestMap(tagManifests);
        Files.walkFileTree(rootDir, new CreateTagManifestsVistor(tagFilesMap, true));
        replaceManifests(tagManifests, tagFilesMap);
        ManifestWriter.writeTagManifests(tagManifests, bagitDir, rootDir, fileEncoding);
    }

    protected void modifyPayloads(Set<Manifest> payLoadManifests) throws NoSuchAlgorithmException, IOException {
        var payloadFilesMap = getManifestToDigestMap(payLoadManifests);
        Files.walkFileTree(getDataDir(bag), new CreatePayloadManifestsVistor(payloadFilesMap, true));
        replaceManifests(payLoadManifests, payloadFilesMap);
    }

    private static void replaceManifests(Set<Manifest> payLoadManifests, Map<Manifest, MessageDigest> payloadFilesMap) {
        payLoadManifests.clear();
        payLoadManifests.addAll(payloadFilesMap.keySet());
    }

    private static Map<Manifest, MessageDigest> getManifestToDigestMap(Set<Manifest> manifests) throws NoSuchAlgorithmException {
        var algorithms = manifests.stream().map(Manifest::getAlgorithm).toList();
        return createManifestToMessageDigestMap(algorithms);
    }


    public static void removePayloads(Path bagDir, List<Path> filesWithNoneNone)
        throws IOException, NoSuchAlgorithmException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException, InvalidBagitFileFormatException {
        new ManifestsUpdater(bagDir) {

            private final Path dataDir = bagDir.resolve("data");

            @Override
            public void modifyPayloads(Set<Manifest> payLoadManifests) {
                payLoadManifests.forEach(this::removeNoneNone);
            }

            private void removeNoneNone(Manifest manifest) {
                manifest.getFileToChecksumMap().keySet().removeIf(this::isInNoneNone);
            }

            private boolean isInNoneNone(Path path) {
                return filesWithNoneNone.contains(dataDir.relativize(path));
            }
        }.updateAll();
    }

}
