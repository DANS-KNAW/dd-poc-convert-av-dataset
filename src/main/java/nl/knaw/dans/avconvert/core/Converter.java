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
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.Files.createDirectories;

@Slf4j
public class Converter {
    @SneakyThrows
    public void convert(Path inputDir, Path mapping, Path outputDir) {
        log.debug("Converting AV dataset from {} to {}", inputDir, outputDir);
        createDirectories(outputDir);
        var revision1BagId = inputDir.toFile().getName();
        var revision1 = outputDir.resolve(revision1BagId);
        var revision2 = outputDir.resolve(UUID.randomUUID().toString());
        var revision3 = outputDir.resolve(UUID.randomUUID().toString());
        var filesXml = readXmlFile(inputDir.resolve("metadata/files.xml"));

        FileUtils.copyDirectory(inputDir.toFile(), revision1.toFile());
        new AVReplacer(revision1, mapping, filesXml).replaceAVFiles();

        FileUtils.copyDirectory(revision1.toFile(), revision2.toFile());
        var bag2 = new BagVersion2(revision2);
        bag2.addVersionOf(revision1BagId);
        bag2.updateManifests(bag2.removeNoneNone(filesXml));

        // TODO 3rd revision bag: add springfield files
    }

    public static Document readXmlFile(Path path) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);

        return factory
            .newDocumentBuilder()
            .parse(path.toFile());
    }

}
