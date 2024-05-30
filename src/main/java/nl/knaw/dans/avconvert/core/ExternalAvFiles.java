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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Slf4j
public class ExternalAvFiles {

    private final Path bagDir;
    private final Path avDir;
    private final Map<String, Path> fileIdToExternalLocationMap;
    private final Map<String, Path> fileIdToBagLocationMap;
    private final String parentOfInputBag;

    public ExternalAvFiles(Path bagDir, Path csv, Path avDir, Document filesXml, Path parentOfInputBag)
        throws IOException {

        this.bagDir = bagDir;
        this.avDir = avDir;
        fileIdToExternalLocationMap = readCSV(csv);
        fileIdToBagLocationMap = getIdentifierToDestMap(filesXml);
        this.parentOfInputBag = parentOfInputBag.getFileName().toString();
        crossCheckReplacedMapped();
    }

    public void replaceAVFiles() {
        fileIdToBagLocationMap.keySet().forEach(this::replaceFile);
    }

    private void crossCheckReplacedMapped() throws IOException {
        var bagParent = parentOfInputBag;
        var replacedFileIds = fileIdToBagLocationMap.keySet();
        var mappedFileIds = new HashSet<>(fileIdToExternalLocationMap.keySet().stream()
            // when reading the csv, the values are prefixed with the avDir, so we can't use startsWith,
            // the bagParent is supposed to be a UUID hence unique
            .filter(kv -> fileIdToExternalLocationMap.get(kv).toString().contains(bagParent))
            .toList()
        );

        // Create sets for the differences
        Set<String> onlyInMapping = new HashSet<>(mappedFileIds);
        onlyInMapping.removeAll(replacedFileIds);
        Set<String> onlyInReplaced = new HashSet<>(replacedFileIds);
        onlyInReplaced.removeAll(mappedFileIds);

        // Log the differences
        if (!onlyInMapping.isEmpty())
            log.error("Elements in fileIdsInMapping but not in replacedFileIds: {} {}", bagParent, onlyInMapping);
        if (!onlyInReplaced.isEmpty())
            log.error("Elements in replacedFileIds but not in fileIdsInMapping: {} {}", bagParent, onlyInReplaced);
        try {
            if (!onlyInReplaced.isEmpty() || !onlyInMapping.isEmpty())
                throw new IllegalStateException("Mapping and replaced files do not match");

            mappedFileIds.forEach(id -> {
                var path = fileIdToExternalLocationMap.get(id);
                if (!path.toFile().exists()) {
                    var msg = "File %s not found: %s".formatted(id, path);
                    log.error(msg);
                    throw new IllegalStateException(msg);
                }
            });
        }
        catch (IllegalStateException e) {
            // TODO call constructor before creating copy of the bag
            FileUtils.deleteDirectory(bagDir.toFile());
            throw e;
        }

    }

    private void replaceFile(String key) {
        var externalLocation = fileIdToExternalLocationMap.get(key);
        if (isEmpty(externalLocation)) {
            log.warn("No external location found for: {}", key);
        }
        else {
            try {
                FileUtils.copyFile(
                    externalLocation.toFile(),
                    bagDir
                        .resolve(fileIdToBagLocationMap.get(key))
                        .toFile()
                );
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map<String, Path> readCSV(Path filePath) throws IOException {
        Map<String, Path> records = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(filePath);
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())) {

            for (CSVRecord csvRecord : csvParser) {
                var pathInAvDir = csvRecord.get("path_in_AV_dir");
                if (isNotEmpty(pathInAvDir))
                    records.put(csvRecord.get("easy_file_id"), avDir.resolve(pathInAvDir));
                else
                    log.warn("No AV path found for: {}", csvRecord);
            }
        }
        return records;
    }

    private Map<String, Path> getIdentifierToDestMap(Document filesXml) throws IOException {
        Map<String, Path> identifierToDestMap = new HashMap<>();

        var fileNodes = filesXml.getElementsByTagName("file");
        for (int i = 0; i < fileNodes.getLength(); i++) {
            var fileElement = (Element) fileNodes.item(i);
            if (fileElement.getElementsByTagName("dct:source").getLength() > 0) {
                NodeList identifierNodes = fileElement.getElementsByTagName("dct:identifier");
                if (identifierNodes.getLength() == 0) {
                    log.error("No <dct:identifier> found in: {}", serializeNode(fileElement));
                }
                else {
                    var filePath = fileElement.getAttribute("filepath");
                    if (isEmpty(filePath)) {
                        log.error("No filepath attribute found in: {}", serializeNode(fileElement));
                    }
                    else if (0 == Files.size(bagDir.resolve(filePath))) {
                        var identifier = identifierNodes.item(0).getTextContent();
                        identifierToDestMap.put(identifier, Path.of(filePath));
                    }
                }
            }
        }
        return identifierToDestMap;
    }

    public static String serializeNode(Node node) {
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
