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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.text.MessageFormat.format;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Slf4j
public class SpringfieldFiles {
    private final Document filesXml;
    Map<String, Path> idToPathInSpringfield;
    HashMap<String, Element> matchingFiles;

    public SpringfieldFiles(Path mappingCsv, Path springfieldDir, String inputBagParent, Document filesXml) throws IOException {
        this.filesXml = filesXml;
        idToPathInSpringfield = findSpringfieldFiles(mappingCsv, springfieldDir, inputBagParent);
        matchingFiles = new HashMap<>();

        var ids = idToPathInSpringfield.keySet().stream().toList();
        var oldFileList = filesXml.getElementsByTagName("file");
        for (int i = 0; i < oldFileList.getLength(); i++) {
            Element file = (Element) oldFileList.item(i);
            var elements = file.getElementsByTagName("dct:identifier");
            if (elements.getLength() != 0) {
                Element identifier = (Element) elements.item(0);
                if (ids.contains(identifier.getTextContent())) {
                    matchingFiles.put(identifier.getTextContent(), file);
                }
            }
        }
        if (!matchingFiles.keySet().containsAll(ids)) {
            var msg = "Not all files found in files.xml: %s %s".formatted(ids, matchingFiles.keySet());
            log.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    public boolean hasFilesToAdd() {
        return !matchingFiles.keySet().isEmpty();
    }

    public Map<String, Path> findSpringfieldFiles(Path mappingCsv, Path springfieldDir, String inputBagParent) throws IOException {
        Map<String, Path> records = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(mappingCsv);
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())) {

            for (CSVRecord csvRecord : csvParser) {
                var pathInSpringfieldDir = csvRecord.get("path_in_springfield_dir");
                var pathInAvDir = csvRecord.get("path_in_AV_dir");
                if (isNotEmpty(pathInSpringfieldDir) && pathInAvDir.startsWith(inputBagParent)) {
                    var path = springfieldDir.resolve(pathInSpringfieldDir);
                    if (path.toFile().exists())
                        records.put(csvRecord.get("easy_file_id"), path);
                    else {
                        var message = format("File does not exist in Springfield directory: {0} -- {1}", inputBagParent, pathInSpringfieldDir);
                        log.error(message);
                        throw new IOException(message);
                    }
                }
            }
        }
        return records;
    }

    public void addSpringfieldFiles(Path outputBagDir) {

        List<Node> newFileList = new ArrayList<>();

        matchingFiles.keySet().forEach(id -> {
            var oldFileElement = (Element) matchingFiles.get(id);
            var springfieldExtension = getExtension(idToPathInSpringfield.get(id).toString());
            var newPath = getNewPath(springfieldExtension, oldFileElement.getAttribute("filepath"));

            var newElement = filesXml.createElement("file");
            newElement.setAttribute("filepath", newPath);
            newElement.appendChild(newRightsElement("accessibleToRights", filesXml, oldFileElement));
            newElement.appendChild(newRightsElement("visibleToRights", filesXml, oldFileElement));
            newFileList.add(newElement);
            try {
                Files.copy(idToPathInSpringfield.get(id), outputBagDir.resolve(newPath));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Add the new list of files to the <files> element
        for (Node newFile : newFileList) {
            filesXml.getElementsByTagName("files").item(0).appendChild(newFile);
        }
    }

    private static String getNewPath(String newExtension, String oldPath) {
        var oldExtension = getExtension(oldPath);
        if (oldExtension.equals(newExtension)) {
            return removeExtension(oldPath) + "-streaming." + newExtension;
        }
        else {
            return removeExtension(oldPath) + "." + newExtension;
        }
    }

    private static Element newRightsElement(String tag, Document filesXml, Element oldFileElement) {
        var oldRights = (Element) oldFileElement.getElementsByTagName(tag).item(0);
        var rightsElement = filesXml.createElement(oldRights.getTagName());
        rightsElement.setTextContent(oldRights.getTextContent());
        return rightsElement;
    }
}
