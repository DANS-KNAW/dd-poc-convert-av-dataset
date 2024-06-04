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
import nl.knaw.dans.bagit.util.PathUtils;
import nl.knaw.dans.bagit.writer.ManifestWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nl.knaw.dans.bagit.hash.Hasher.createManifestToMessageDigestMap;

public abstract class BagUpdater {

    private final Charset fileEncoding;
    private final Path rootDir;
    private final Path bagitDir;
    private final Bag bag;

    private BagUpdater(Bag bag) {
        fileEncoding = bag.getFileEncoding();
        rootDir = bag.getRootDir();
        bagitDir = PathUtils.getBagitDir(bag);

        this.bag = bag;
    }

    protected void updateTagAndPayloadManifests()
        throws NoSuchAlgorithmException, IOException {

        // TODO Do the datasets have other big files?
        //  Then override visitFile and reuse values for paths not in fileIdToBagLocationMap.values().
        var payLoadManifests = bag.getPayLoadManifests();
        modifyPayloads(payLoadManifests);
        ManifestWriter.writePayloadManifests(payLoadManifests, bagitDir, rootDir, fileEncoding);

        var tagManifests = bag.getTagManifests();
        var tagFilesMap = getManifestToDigestMap(tagManifests);
        var visitor = getTagManifestsVistor(tagFilesMap);
        Files.walkFileTree(rootDir, visitor);
        replaceManifests(tagManifests, tagFilesMap);
        ManifestWriter.writeTagManifests(tagManifests, bagitDir, rootDir, fileEncoding);
    }

    private CreateTagManifestsVistor getTagManifestsVistor(Map<Manifest, MessageDigest> tagFilesMap) {
        // copied from dd-ingest-flow
        return new CreateTagManifestsVistor(tagFilesMap, true) {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                /*
                 * Fix for EASY-1306: a tag manifest must not contain an entry for itself, as this is practically
                 * impossible to calculate. It could in theory contain entries for other tag manifests. However,
                 * the CreateTagManifestsVistor, once it finds an entry for a tag file in ONE of the tag manifests,
                 * will add an entry in ALL tag manifests.
                 *
                 * Therefore, we adopt the strategy NOT to calculate any checksums for the tag manifests themselves.
                 *
                 * Update: this is actually required in V1.0: https://tools.ietf.org/html/rfc8493#section-2.2.1
                 */
                var isTagManifest = rootDir.relativize(path).getNameCount() == 1 &&
                                    path.getFileName().toString().startsWith("tagmanifest-");

                if (isTagManifest) {
                    return FileVisitResult.CONTINUE;
                }
                else {
                    return super.visitFile(path, attrs);
                }
            }

        };
    }

    protected abstract void modifyPayloads(Set<Manifest> payLoadManifests) throws NoSuchAlgorithmException, IOException;

    private static void replaceManifests(Set<Manifest> payLoadManifests, Map<Manifest, MessageDigest> payloadFilesMap) {
        payLoadManifests.clear();
        payLoadManifests.addAll(payloadFilesMap.keySet());
    }

    private static Map<Manifest, MessageDigest> getManifestToDigestMap(Set<Manifest> manifests) throws NoSuchAlgorithmException {
        var algorithms = manifests.stream().map(Manifest::getAlgorithm).toList();
        return createManifestToMessageDigestMap(algorithms);
    }

    public static void updateAllPayloads(Bag bag)
        throws IOException, NoSuchAlgorithmException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException, InvalidBagitFileFormatException {
        new BagUpdater(bag) {

            protected void modifyPayloads(Set<Manifest> payLoadManifests) throws NoSuchAlgorithmException, IOException {
                var payloadFilesMap = getManifestToDigestMap(payLoadManifests);
                Files.walkFileTree(bag.getRootDir().resolve("data"), new CreatePayloadManifestsVistor(payloadFilesMap, true));
                replaceManifests(payLoadManifests, payloadFilesMap);
            }

        }.updateTagAndPayloadManifests();
    }

    public static void removePayloads(Bag bag, List<Path> filesWithNoneNone)
        throws IOException, NoSuchAlgorithmException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException, InvalidBagitFileFormatException {
        new BagUpdater(bag) {

            @Override
            protected void modifyPayloads(Set<Manifest> payLoadManifests) {
                payLoadManifests.forEach(this::removeNoneNone);
            }

            private void removeNoneNone(Manifest manifest) {
                manifest.getFileToChecksumMap().keySet().removeIf(this::isInNoneNone);
            }

            private boolean isInNoneNone(Path path) {
                return filesWithNoneNone.contains(bag.getRootDir().relativize(path));
            }
        }.updateTagAndPayloadManifests();
    }
}
