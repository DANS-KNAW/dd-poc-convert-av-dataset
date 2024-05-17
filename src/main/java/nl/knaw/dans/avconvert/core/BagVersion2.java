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
import nl.knaw.dans.bagit.domain.Manifest;
import nl.knaw.dans.bagit.exceptions.InvalidBagitFileFormatException;
import nl.knaw.dans.bagit.exceptions.MaliciousPathException;
import nl.knaw.dans.bagit.exceptions.UnparsableVersionException;
import nl.knaw.dans.bagit.exceptions.UnsupportedAlgorithmException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BagVersion2 {

    private final Path bagDir;

    public BagVersion2(Path bagDir) {
        this.bagDir = bagDir;
    }

    @SneakyThrows
    public List<Path> removeNoneNone(Document filesXml) {

        List<Path> filesWithNoneNone = new ArrayList<>();
        NodeList fileList = filesXml.getElementsByTagName("file");
        for (int i = 0; i < fileList.getLength(); i++) {
            Element fileElement = (Element) fileList.item(i);
            if (isNone(fileElement, "accessibleToRights") && isNone(fileElement, "visibleToRights")) {
                var filepath = fileElement.getAttribute("filepath");
                filesWithNoneNone.add(Path.of(filepath));
                fileElement.getParentNode().removeChild(fileElement);
                if (!bagDir.resolve("data").resolve(filepath).toFile().delete()) {
                    throw new IOException("Could not delete " + filepath);
                }
                ;
                // Since we're modifying the list we're iterating over, decrement i to adjust for the next iteration.
                i--;
            }
        }
        Writer writer = new FileWriter(bagDir.resolve("metadata").resolve("files.xml").toFile());
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(filesXml), new StreamResult(writer));
        return filesWithNoneNone;
    }

    private static boolean isNone(Node fileElement, String tag) {
        var elements = ((Element) fileElement).getElementsByTagName(tag);
        if (elements.getLength() == 0)
            return true;
        return "NONE".equals(elements.item(0).getTextContent());
    }

    public void updateManifests(List<Path> filesWithNoneNone)
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
        }.update();
    }

    public void addVersionOf(String previousVersion) throws IOException {
        Files.writeString(bagDir.resolve("bag-info.txt"),
            "Is-Version-Of: urn:uuid:" + previousVersion + System.lineSeparator(),
            StandardOpenOption.APPEND
        );
    }
}
