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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NoneNoneFiles {

    private final Path bagDir;

    public NoneNoneFiles(Path bagDir) {
        this.bagDir = bagDir;
    }

    public List<Path> removeNoneNone(Document mutatedFilesXml) throws IOException {

        List<Path> filesWithNoneNone = new ArrayList<>();
        NodeList fileList = mutatedFilesXml.getElementsByTagName("file");
        for (int i = 0; i < fileList.getLength(); i++) {
            Element fileElement = (Element) fileList.item(i);
            if (isNone(fileElement, "accessibleToRights") && isNone(fileElement, "visibleToRights")) {
                var filepath = fileElement.getAttribute("filepath");
                filesWithNoneNone.add(Path.of(filepath));
                fileElement.getParentNode().removeChild(fileElement);
                var file = bagDir.resolve(filepath).toFile();
                if (!file.delete()) {
                    throw new IOException("Could not delete " + file);
                }
                // Since we're modifying the list we're iterating over, decrement i to adjust for the next iteration.
                i--;
            }
        }
        return filesWithNoneNone;
    }

    private static boolean isNone(Node fileElement, String tag) {
        var elements = ((Element) fileElement).getElementsByTagName(tag);
        if (elements.getLength() == 0)
            return true;
        return "NONE".equals(elements.item(0).getTextContent());
    }
}
