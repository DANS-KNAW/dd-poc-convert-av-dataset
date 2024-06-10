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
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static nl.knaw.dans.avconvert.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class SpringfieldFilesTest extends AbstractTestWithTestDir {

    private final Path integrationDir = Path.of("src/test/resources/integration/");
    private final Path bagDir = integrationDir.resolve("input-bags/7bf09491-54b4-436e-7f59-1027f54cbb0c/a5ad806e-d5c4-45e6-b434-f42324d4e097");
    private final Path springfieldDir = integrationDir.resolve("springfield-dir");
    private final String bagParent = bagDir.getParent().getFileName().toString();

    @Test
    public void constructor_should_throw_not_found_in_springfield_dir() throws Exception {
        captureStdout();
        var filesXmlDoc = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        var emptySpringfieldDir = testDir.resolve("is-empty");
        var mappingCsv = integrationDir.resolve("mapping.csv");

        createDirectories(emptySpringfieldDir);

        assertThatThrownBy(() -> new SpringfieldFiles(mappingCsv, emptySpringfieldDir, bagParent, filesXmlDoc))
            .isInstanceOf(IOException.class)
            .hasMessage(
                "File does not exist in Springfield directory: 7bf09491-54b4-436e-7f59-1027f54cbb0c -- domain/dans/user/Caleidoscoop_Film/video/31/rawvideo/2/GV_CaleidoscoopFilm_ingekwartierd_08.mp4");
    }

    @Test
    public void constructor_should_throw_not_found_mapping() {
        assertThatThrownBy(() -> new SpringfieldFiles(testDir.resolve("does-not-exist.csv"), springfieldDir, bagParent, null))
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/SpringfieldFilesTest/does-not-exist.csv");
    }

    @Test
    public void constructor_is_happy_without_springfield_files() throws Exception {
        var filesXmlDoc = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        var empty = testDir.resolve("empty.csv");
        FileUtils.touch(empty.toFile());
        var springfieldFiles = new SpringfieldFiles(empty, springfieldDir, bagParent, filesXmlDoc);
        assertThat(springfieldFiles.hasFilesToAdd()).isFalse();
    }

    @Test
    public void constructor_should_throw_not_found_in_files_xml() throws Exception {
        captureStdout();
        var filesXmlDoc = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        createDirectories(testDir);

        var extendedMapping = testDir.resolve("mapping.csv");
        copy(integrationDir.resolve("mapping.csv"), extendedMapping);
        Files.writeString(extendedMapping,
            System.lineSeparator()
            + "easy-file:1234,easy-dataset:41418,%s/bag/data/don-t-care.txt,domain/dans/user/NIOD/video/148/rawvideo/2/JKKV_2007_Eindpunt_Sobibor_SCHELVIS.mp4"
                .formatted(bagParent)
            + System.lineSeparator(),
            StandardOpenOption.APPEND);

        assertThatThrownBy(() -> new SpringfieldFiles(extendedMapping, springfieldDir, bagParent, filesXmlDoc))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Not all files found in files.xml: [easy-file:1234, easy-file:5455618] [easy-file:5455618]");
    }
}
