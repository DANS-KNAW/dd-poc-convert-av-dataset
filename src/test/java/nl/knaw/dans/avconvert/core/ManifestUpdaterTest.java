package nl.knaw.dans.avconvert.core;

import nl.knaw.dans.avconvert.AbstractTestWithTestDir;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static ch.qos.logback.core.util.FileUtil.createMissingParentDirectories;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.writeString;
import static nl.knaw.dans.avconvert.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;

public class ManifestUpdaterTest extends AbstractTestWithTestDir {

    private final Path bagDir = testDir.resolve("bag");

    @Test
    public void testUpdateManifest() throws Exception {
        var payloadManifest = bagDir.resolve("manifest-sha1.txt");
        createMissingParentDirectories(payloadManifest.toFile());
        createDirectory(bagDir.resolve("data"));
        copy(Paths.get("src/test/resources/springfield/swirls.mp4"), bagDir.resolve("data/file4.mp4"));
        Files.writeString(bagDir.resolve("bagit.txt"), """
            BagIt-Version: 1.0
            Tag-File-Character-Encoding: UTF-8
            """);
        createFile(bagDir.resolve("tagmanifest-sha1.txt"));
        writeString(payloadManifest, """
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file1.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file2.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file3.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file4.mp4
            """
        );
        captureStdout(); // ignore the logging on stdout

        ManifestsUpdater.removePayloads(bagDir, Arrays.asList(
            Path.of("file2.mp4"),
            Path.of("file3.mp4")
        ));
        assertThat(Files.readString(payloadManifest)).isEqualTo("""
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file1.mp4
            0a4d55a8d778e5022fab701977c5d840bbc486d0  data/file4.mp4
            """
        );
    }
}
