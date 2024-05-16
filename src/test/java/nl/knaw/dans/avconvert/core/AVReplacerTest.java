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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import nl.knaw.dans.avconvert.AbstractTestWithTestDir;
import org.h2.store.fs.FileUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.createDirectories;
import static nl.knaw.dans.avconvert.TestUtils.captureLog;
import static nl.knaw.dans.avconvert.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AVReplacerTest extends AbstractTestWithTestDir {

    Path bagDir = testDir.resolve("bag");
    Path filesXml = bagDir.resolve("metadata/files.xml");
    Path csvFile = testDir.resolve("mapping.csv");

    @Test
    public void constructor_should_throw_no_csv() {
        assertThatThrownBy(() -> new AVReplacer(bagDir, csvFile))
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage(testDir.resolve("mapping.csv").toString());
    }

    @Test
    public void should_replace() throws Exception {
        createDirectories(csvFile.getParent());
        createDirectories(filesXml.getParent());
        createDirectories(bagDir.resolve("data"));
        FileUtils.createFile(bagDir.resolve("data/file1.mp4").toString());
        FileUtils.createFile(bagDir.resolve("data/file2.mp4").toString());
        Files.writeString(bagDir.resolve("bagit.txt"), """
            BagIt-Version: 1.0
            Tag-File-Character-Encoding: UTF-8
            """);
        Files.createFile(bagDir.resolve("tagmanifest-sha1.txt"));
        Files.createFile(bagDir.resolve("manifest-sha1.txt"));
        Files.writeString(csvFile, """
            easy-file-id,path-in-AV-dir,path-in-springfield-dir
            file1,%s,
            file2,,%s
            skipped,,""".formatted(
            Paths.get("src/test/resources/avDir/marbles.mp4"),
            Paths.get("src/test/resources/springfield/swirls.mp4")
        ));
        Files.writeString(filesXml, """
            <files
                    xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                    xmlns:dct="http://purl.org/dc/terms/">
              <file filepath="file1.mp4">
                <dct:identifier>file1</dct:identifier>
                <dct:source>just has to be present</dct:source>
              </file>
              <file filepath="file2.mp4">
                <dct:identifier>file2</dct:identifier>
                <dct:source>don't care for this test</dct:source>
              </file>
              <file filepath="file3.mp4">
                <dct:source>generates logging</dct:source>
              </file>
              <file>
                <dct:identifier>file4</dct:identifier>
                <dct:source>generates logging</dct:source>
              </file>
            </files>
            """
        );

        assertThat(bagDir.resolve("data/file1.mp4")).hasSize(0L);
        assertThat(bagDir.resolve("data/file2.mp4")).hasSize(0L);
        assertThat(bagDir.resolve("manifest-sha1.txt")).hasSize(0L);
        assertThat(bagDir.resolve("tagmanifest-sha1.txt")).hasSize(0L);
        var logger = captureLog(Level.INFO, AVReplacer.class.getName());
        captureStdout(); // ignore the logging on stdout

        new AVReplacer(bagDir, csvFile).replaceAVFiles();

        assertThat(bagDir.resolve("data/file1.mp4")).hasSize(4918979L);
        assertThat(bagDir.resolve("data/file2.mp4")).hasSize(0L);
        assertThat(bagDir.resolve("manifest-sha1.txt")).hasContent("""
            da39a3ee5e6b4b0d3255bfef95601890afd80709  data/file2.mp4
            ab8e3b0d1cb0b5f057703257e87b7903b24d8890  data/file1.mp4
            """);
        assertThat(bagDir.resolve("tagmanifest-sha1.txt")).hasContent("""
            8010d7758f1793d0221c529fef818ff988dda141  bagit.txt
            da39a3ee5e6b4b0d3255bfef95601890afd80709  tagmanifest-sha1.txt
            3e0eff6102310a45d607a6f34b03c9d77bf98b01  metadata/files.xml
            0412a92d67d0c42d15fb3a31297f0db477d1fe93  manifest-sha1.txt
            """);

        var messages = logger.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages.get(0)).isEqualTo("""
            No AV path found for: CSVRecord [comment='null', recordNumber=2, values=[file2, , src/test/resources/springfield/swirls.mp4]]""");
        assertThat(messages.get(1)).isEqualTo("""
            No AV path found for: CSVRecord [comment='null', recordNumber=3, values=[skipped, , ]]""");
        assertThat(messages.get(2)).isEqualTo("""
            No <dct:identifier> found in: <?xml version="1.0" encoding="UTF-8"?><file filepath="file3.mp4" xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/">
                <dct:source xmlns:dct="http://purl.org/dc/terms/">generates logging</dct:source>
              </file>""");
        assertThat(messages.get(3)).isEqualTo("""
            No filepath attribute found in: <?xml version="1.0" encoding="UTF-8"?><file xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/">
                <dct:identifier xmlns:dct="http://purl.org/dc/terms/">file4</dct:identifier>
                <dct:source xmlns:dct="http://purl.org/dc/terms/">generates logging</dct:source>
              </file>"""
        );
    }
}