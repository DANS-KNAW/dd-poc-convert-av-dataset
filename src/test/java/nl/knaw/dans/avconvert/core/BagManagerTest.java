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
import nl.knaw.dans.bagit.domain.Version;
import nl.knaw.dans.bagit.reader.BagReader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static ch.qos.logback.core.util.FileUtil.createMissingParentDirectories;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.writeString;
import static nl.knaw.dans.avconvert.TestUtils.captureStdout;
import static nl.knaw.dans.bagit.writer.BagitFileWriter.writeBagitFile;
import static org.assertj.core.api.Assertions.assertThat;

public class BagManagerTest extends AbstractTestWithTestDir {

    private final Path bagDir = testDir.resolve("bag");

    @Test
    public void testUpdateManifest() throws Exception {
        var dataFile = bagDir.resolve("data/file4.mp4");
        createMissingParentDirectories(dataFile.toFile());
        copy(Paths.get("src/test/resources/springfield/swirls.mp4"), dataFile);

        writeBagitFile(new Version(1, 0), StandardCharsets.UTF_8, bagDir);
        createFile(bagDir.resolve("tagmanifest-sha1.txt"));

        var payloadManifest = bagDir.resolve("manifest-sha1.txt");
        writeString(payloadManifest, """
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file1.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file2.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file3.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file4.mp4
            """
        );
        captureStdout(); // ignore the logging on stdout

        BagManager.removePayloads(new BagReader().read(bagDir), Arrays.asList(
            Path.of("data/file2.mp4"),
            Path.of("data/file3.mp4")
        ));
        assertThat(Files.readString(payloadManifest)).isEqualTo("""
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file1.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file4.mp4
            """
        );
    }
}
