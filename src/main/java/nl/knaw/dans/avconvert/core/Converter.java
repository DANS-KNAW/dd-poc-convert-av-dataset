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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.bagit.reader.BagReader;

import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.Files.createDirectories;
import static nl.knaw.dans.avconvert.core.XmlUtil.readXml;
import static org.apache.commons.io.FileUtils.copyDirectory;

@Slf4j
public class Converter {

    @SneakyThrows
    public void convert(Path inputBagDir, Path mapping, Path avDir, Path springfieldDir, Path outputDir) {
        log.debug("Converting AV dataset from {} to {}", inputBagDir, outputDir);
        createDirectories(outputDir);
        var inputBagParentName = inputBagDir.getParent().toFile().getName();
        var revision1 = outputDir.resolve(inputBagParentName).resolve(inputBagDir.getFileName());
        var revision2 = outputDir.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString());
        var revision3 = outputDir.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString());
        var filesXmlDocument = readXml(inputBagDir.resolve("metadata/files.xml"));

        copyDirectory(inputBagDir.toFile(), revision1.toFile());
        new ExternalAvFiles(revision1, mapping, avDir, filesXmlDocument, inputBagParentName).replaceAVFiles();
        XmlUtil.writeFilesXml(revision1, filesXmlDocument);
        BagManager.updateManifests(new BagReader().read(revision1));

        copyDirectory(revision1.toFile(), revision2.toFile());
        var removedFiles = new NoneNoneFiles(revision2).removeNoneNone(filesXmlDocument);
        XmlUtil.writeFilesXml(revision2, filesXmlDocument);
        BagManager.removePayloadsFromManifest(BagManager.updateBagVersion(revision2, revision1), removedFiles);

        var sfFiles = new SpringfieldFiles(mapping, springfieldDir, inputBagParentName, filesXmlDocument);
        if (sfFiles.hasFilesToAdd()) {
            copyDirectory(revision2.toFile(), revision3.toFile());
            sfFiles.addSpringfieldFiles(revision3);
            XmlUtil.writeFilesXml(revision3, filesXmlDocument);
            BagManager.updateManifests(BagManager.updateBagVersion(revision3, revision2));
        }
    }
}
