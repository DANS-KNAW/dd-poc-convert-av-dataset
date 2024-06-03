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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.nio.file.Files.createDirectories;
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
        var filesXml = readXml(inputBagDir.resolve("metadata/files.xml"));

        copyDirectory(inputBagDir.toFile(), revision1.toFile());
        new ExternalAvFiles(revision1, mapping, avDir, filesXml, inputBagParentName).replaceAVFiles();
        writeFilesXml(revision1, filesXml);
        ManifestsUpdater.updateAllPayloads(revision1);

        copyDirectory(revision1.toFile(), revision2.toFile());
        var removedFiles = new NoneNoneFiles(revision2).removeNoneNone(filesXml);
        addIsVersionOf(revision2, revision1);
        writeFilesXml(revision2, filesXml);
        ManifestsUpdater.removePayloads(revision2, removedFiles);

        var sfFiles = new SpringfieldFiles(mapping, springfieldDir, inputBagParentName, filesXml);
        if (sfFiles.hasFilesToAdd()) {
            copyDirectory(revision2.toFile(), revision3.toFile());
            sfFiles.addSpringfieldFiles(revision3);
            replaceIsVersionOf(revision3, revision2);
            writeFilesXml(revision3, filesXml);
            ManifestsUpdater.updateAllPayloads(revision3);
        }
    }

    private static void addIsVersionOf(Path newBag, Path previousBag) throws IOException, ParserConfigurationException, SAXException {
        var lines = new ArrayList<String>();
        var idTypes = List.of("DOI", "URN");
        var idElements = ((Element) readXml(newBag.resolve("metadata/dataset.xml"))
            .getElementsByTagName("ddm:dcmiMetadata").item(0))
            .getElementsByTagName("dct:identifier");
        for (int i = 0; i < idElements.getLength(); i++) {
            var id = (Element) idElements.item(i);
            var idType = id.getAttribute("xsi:type")
                .replace("id-type:","");
            if (idTypes.contains(idType)) {
                lines.add("Base-%s: %s".formatted(idType, id.getTextContent()));
            }
        }
        lines.add("Is-Version-Of: urn:uuid:" + previousBag.getParent().getFileName());
        Files.writeString(newBag.resolve("bag-info.txt"),
            String.join(System.lineSeparator(), lines) + System.lineSeparator(),
            StandardOpenOption.APPEND
        );
    }

    private static void replaceIsVersionOf(Path newBag, Path previousBag) throws IOException {
        var bagInfo = newBag.resolve("bag-info.txt");
        var lines = Files.readAllLines(bagInfo);
        lines.set(lines.size() - 1, "Is-Version-Of: urn:uuid:" + previousBag.getParent().getFileName());
        Files.write(bagInfo, lines);
    }

    public static Document readXml(Path path) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);

        return factory
            .newDocumentBuilder()
            .parse(path.toFile());
    }

    public static void writeFilesXml(Path bagDir, Document filesXml) throws IOException, TransformerException {
        Writer writer = new FileWriter(bagDir.resolve("metadata").resolve("files.xml").toFile());
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(filesXml), new StreamResult(writer));
    }
}
