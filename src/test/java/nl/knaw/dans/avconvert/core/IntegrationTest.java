package nl.knaw.dans.avconvert.core;

import nl.knaw.dans.avconvert.AbstractTestWithTestDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest extends AbstractTestWithTestDir {

    private static Path sources = Paths.get("src/test/resources/integration/");

    private static Stream<Path> bagProvider() throws IOException {
        var bagParents = sources.resolve("input-bags");
        return Files.walk(bagParents, 2).filter(path ->
            path.getParent().equals(bagParents)
        );
    }

    @ParameterizedTest
    @MethodSource("bagProvider")
    public void testGrandchild(Path inputBag) {
        new Converter().convert(
            inputBag,
            sources.resolve("mapping.csv"),
            sources.resolve("av-dir"),
            sources.resolve("springfield-dir"),
            testDir.resolve("converted-bags")
        );
        assertThat(inputBag.resolve("manifest.")).exists();
        // TODO validate created bags and is-version-of chain
    }
}