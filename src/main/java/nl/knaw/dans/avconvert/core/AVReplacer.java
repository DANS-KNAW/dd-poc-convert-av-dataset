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
import nl.knaw.dans.bagit.creator.CreatePayloadManifestsVistor;
import nl.knaw.dans.bagit.creator.CreateTagManifestsVistor;
import nl.knaw.dans.bagit.domain.Manifest;
import nl.knaw.dans.bagit.reader.BagReader;
import nl.knaw.dans.bagit.util.PathUtils;
import nl.knaw.dans.bagit.writer.ManifestWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static nl.knaw.dans.bagit.hash.Hasher.createManifestToMessageDigestMap;
import static nl.knaw.dans.bagit.util.PathUtils.getDataDir;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Slf4j
public class AVReplacer {
    private static final Logger logger = LoggerFactory.getLogger(AVReplacer.class);

    private final Path bagDir;
    private final Map<String, String> fileIdToExternalLocationMap;
    private final Map<String, String> fileIdToBagLocationMap;

    public AVReplacer(Path bagDir, Path csv)
        throws IOException, ParserConfigurationException, SAXException {

        this.bagDir = bagDir;
        fileIdToExternalLocationMap = readCSV(csv);
        fileIdToBagLocationMap = getIdentifierToDestMap(readXmlFile(bagDir.resolve("metadata/files.xml")));
    }

    @SneakyThrows
    public void replaceAVFiles() {
        fileIdToBagLocationMap.keySet().forEach(this::replaceFile);

        var bag = new BagReader().read(bagDir);
        var fileEncoding = bag.getFileEncoding();
        var rootDir = bag.getRootDir();
        var bagitDir = PathUtils.getBagitDir(bag);

        // TODO Do the datasets have other big files?
        //  Then override visitFile and reuse values for paths not in fileIdToBagLocationMap.values().
        var payLoadManifests = bag.getPayLoadManifests();
        var payloadFilesMap = getManifestToDigestMap(payLoadManifests);
        var payloadVisitor = new CreatePayloadManifestsVistor(payloadFilesMap, true);
        Files.walkFileTree(getDataDir(bag), payloadVisitor);
        replaceManifests(payLoadManifests, payloadFilesMap);
        ManifestWriter.writePayloadManifests(payLoadManifests, bagitDir, rootDir, fileEncoding);

        var tagManifests = bag.getTagManifests();
        var tagFilesMap = getManifestToDigestMap(tagManifests);
        var tagFileVisitor = new CreateTagManifestsVistor(tagFilesMap, true);
        Files.walkFileTree(rootDir, tagFileVisitor);
        replaceManifests(tagManifests, tagFilesMap);
        ManifestWriter.writeTagManifests(tagManifests, bagitDir, rootDir, fileEncoding);
    }

    private static void replaceManifests(Set<Manifest> payLoadManifests, Map<Manifest, MessageDigest> payloadFilesMap) {
        payLoadManifests.clear();
        payLoadManifests.addAll(payloadFilesMap.keySet());
    }

    private static Map<Manifest, MessageDigest> getManifestToDigestMap(Set<Manifest> manifests) throws NoSuchAlgorithmException {
        var algorithms = manifests.stream().map(Manifest::getAlgorithm).toList();
        return createManifestToMessageDigestMap(algorithms);
    }

    private void replaceFile(String key) {
        var externalLocation = fileIdToExternalLocationMap.get(key);
        if (isEmpty(externalLocation)) {
            logger.warn("No external location found for: {}", key);
        }
        else {
            try {
                FileUtils.copyFile(
                    new File(externalLocation),
                    bagDir
                        .resolve("data")
                        .resolve(fileIdToBagLocationMap.get(key))
                        .toFile()
                );
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Map<String, String> readCSV(Path filePath) throws IOException {
        Map<String, String> records = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(filePath);
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())) {

            for (CSVRecord csvRecord : csvParser) {
                var pathInAvDir = csvRecord.get("path-in-AV-dir");
                if (isNotEmpty(pathInAvDir))
                    records.put(csvRecord.get("easy-file-id"), pathInAvDir);
                else
                    logger.warn("No AV path found for: {}", csvRecord);
            }
        }
        return records;
    }

    private static Document readXmlFile(Path path) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);

        return factory
            .newDocumentBuilder()
            .parse(path.toFile());
    }

    private static Map<String, String> getIdentifierToDestMap(Document filesXml) {
        Map<String, String> identifierToDestMap = new HashMap<>();

        var fileNodes = filesXml.getElementsByTagName("file");
        for (int i = 0; i < fileNodes.getLength(); i++) {
            var fileElement = (Element) fileNodes.item(i);
            if (fileElement.getElementsByTagName("dct:source").getLength() > 0) {
                NodeList identifierNodes = fileElement.getElementsByTagName("dct:identifier");
                if (identifierNodes.getLength() == 0) {
                    logger.error("No <dct:identifier> found in: {}", serializeNode(fileElement));
                }
                else {
                    var filePath = fileElement.getAttribute("filepath");
                    if (isEmpty(filePath)) {
                        logger.error("No filepath attribute found in: {}", serializeNode(fileElement));
                    }
                    else {
                        var identifier = identifierNodes.item(0).getTextContent();
                        identifierToDestMap.put(identifier, filePath);
                    }
                }
            }
        }
        return identifierToDestMap;
    }

    private static String serializeNode(Node node) {
        try {
            StringWriter sw = new StringWriter();
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString();
        }
        catch (Exception e) {
            return e.getMessage();
        }
    }
}
