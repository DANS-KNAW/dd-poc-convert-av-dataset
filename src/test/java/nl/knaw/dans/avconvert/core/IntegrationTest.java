package nl.knaw.dans.avconvert.core;

import nl.knaw.dans.avconvert.AbstractTestWithTestDir;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Stream;

import static nl.knaw.dans.avconvert.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {

    private static final Path testDir = Path.of("target/test")
        .resolve(IntegrationTest.class.getSimpleName());

    @BeforeAll
    public static void setUpOnce() throws Exception {
        FileUtils.deleteDirectory(testDir.toFile());
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
        System.out.println(inputBag.getFileName());
        captureStdout(); // ignore the logging on stdout
        new Converter().convert(
            inputBag,
            sources.resolve("mapping.csv"),
            sources.resolve("av-dir"),
            sources.resolve("springfield-dir"),
            testDir.resolve("converted-bags")
        );

        // all manifest-sha1.txt files should be unique
        var manifests = new ArrayList<>();
        manifests.add(readSorted(inputBag.resolve("manifest-sha1.txt")));
        Files.walk(testDir.resolve("converted-bags"))
            .filter(path -> path.getFileName().toString().equals("manifest-sha1.txt"))
            .forEach(path -> manifests.add(readSorted(path)));
        assertThat(new HashSet<>(manifests))
            .containsExactlyInAnyOrderElementsOf(manifests);

        // TODO validate bags manually
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