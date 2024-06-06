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

import nl.knaw.dans.avconvert.AbstractTestWithTestDir;
import nl.knaw.dans.avconvert.TestUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class SpringfieldFilesTest extends AbstractTestWithTestDir {
    @Test
    public void constructor_should_throw_not_found_in_springfield_dir() throws IOException, ParserConfigurationException, SAXException {
        TestUtils.captureStdout();
        var outputDir = testDir.resolve("output");
        var springfieldDir = testDir.resolve("is-empty");
        createDirectories(outputDir);
        createDirectories(springfieldDir);

        var bagDir = Path.of("src/test/resources/integration/input-bags/7bf09491-54b4-436e-7f59-1027f54cbb0c/a5ad806e-d5c4-45e6-b434-f42324d4e097");
        var filesXmlDoc = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        var mappingCsv = Path.of("src/test/resources/integration/mapping.csv");
        assertThatThrownBy(() -> new SpringfieldFiles(mappingCsv, springfieldDir, bagDir.getParent().getFileName().toString(), filesXmlDoc))
            .isInstanceOf(IOException.class)
            .hasMessage(
                "File does not exist in Springfield directory: 7bf09491-54b4-436e-7f59-1027f54cbb0c -- domain/dans/user/Caleidoscoop_Film/video/31/rawvideo/2/GV_CaleidoscoopFilm_ingekwartierd_08.mp4");
    }

    @Test
    public void constructor_should_throw_not_found_in_files_xml() throws IOException, ParserConfigurationException, SAXException {
        TestUtils.captureStdout();
        var outputDir = testDir.resolve("output");
        var springfieldDir = Path.of("src/test/resources/integration/springfield-dir");
        createDirectories(outputDir);

        var mappingCsv = testDir.resolve("mapping.csv");
        copy(Path.of("src/test/resources/integration/mapping.csv"), mappingCsv);
        Files.writeString(mappingCsv, System.lineSeparator()
                                      + "easy-file:1234,easy-dataset:41418,7bf09491-54b4-436e-7f59-1027f54cbb0c/bag/data/bogus.txt,domain/dans/user/NIOD/video/148/rawvideo/2/JKKV_2007_Eindpunt_Sobibor_SCHELVIS.mp4"
                                      + System.lineSeparator(), StandardOpenOption.APPEND);

        var bagDir = Path.of("src/test/resources/integration/input-bags/7bf09491-54b4-436e-7f59-1027f54cbb0c/a5ad806e-d5c4-45e6-b434-f42324d4e097");
        var filesXmlDoc = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        assertThatThrownBy(() -> new SpringfieldFiles(mappingCsv, springfieldDir, bagDir.getParent().getFileName().toString(), filesXmlDoc))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Not all files found in files.xml: [easy-file:1234, easy-file:5455618] [easy-file:5455618]");
    }
}
