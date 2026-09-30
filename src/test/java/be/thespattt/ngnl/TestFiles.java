package be.thespattt.ngnl;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helpers to read the project files from the tests (Gradle runs tests from the project directory).
 */
final class TestFiles {

    private TestFiles() {
    }

    /** Project-relative path. */
    static Path path(String relative) {
        return Paths.get(relative);
    }

    /** Whole text file. */
    static String read(String relative) throws IOException {
        return Files.readString(path(relative), StandardCharsets.UTF_8);
    }

    /** Reader on a text file. */
    static Reader reader(String relative) throws IOException {
        return Files.newBufferedReader(path(relative), StandardCharsets.UTF_8);
    }

    /** Every Java source file of the plugin. */
    static List<Path> mainSources() throws IOException {
        try (Stream<Path> walk = Files.walk(path("src/main/java"))) {
            return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }
}
