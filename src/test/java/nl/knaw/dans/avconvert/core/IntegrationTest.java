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
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Stream;

import static nl.knaw.dans.avconvert.TestUtils.captureLog;
import static nl.knaw.dans.avconvert.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {

    private static final Path testDir = Path.of("target/test")
        .resolve(IntegrationTest.class.getSimpleName());
    private static ListAppender<ILoggingEvent> capturedLog;

    @BeforeAll
    public static void setUpOnce() throws Exception {
        FileUtils.deleteDirectory(testDir.toFile());
        capturedLog = captureLog(Level.DEBUG, "nl.knaw.dans.avconvert");
    }

    @AfterAll
    public static void tearDownOnce() throws IOException {
        var lines = capturedLog.list.stream()
            .map(ILoggingEvent::getFormattedMessage).toList();
        Files.writeString(testDir.resolve("log.txt"), String.join("\n", lines));
    }

    private static final Path sources = Paths.get("src/test/resources/integration/");

    private static Stream<Path> bagProvider() throws IOException {
        var bagParents = sources.resolve("input-bags");
        return Files.walk(bagParents, 3).filter(path ->
            path.getParent().getParent().equals(bagParents)
        );
    }

    @ParameterizedTest
    @MethodSource("bagProvider")
    public void testGrandchild(Path inputBag) throws IOException {
        captureStdout(); // ignore the logging on stdout
        var outputDir = testDir.resolve("converted-bags").resolve(inputBag.getParent().getFileName().toString().substring(0,1));
        new Converter().convert(
            inputBag,
            sources.resolve("mapping.csv"),
            sources.resolve("av-dir"),
            sources.resolve("springfield-dir"),
            outputDir
        );

        // all manifest-sha1.txt files should be unique
        var manifests = new ArrayList<>();
        manifests.add(readSorted(inputBag.resolve("manifest-sha1.txt")));
        Files.walk(outputDir)
            .filter(path -> path.getFileName().toString().equals("manifest-sha1.txt"))
            .forEach(path -> manifests.add(readSorted(path)));
        assertThat(new HashSet<>(manifests))
            .containsExactlyInAnyOrderElementsOf(manifests);

        // TODO validate bags manually, follow instructions in src/test/resources/integration/validate.sh
    }

    private String readSorted(Path path) {
        try {
            return Files.readAllLines(path).stream().sorted().reduce("", (a, b) -> a + b + "\n");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}