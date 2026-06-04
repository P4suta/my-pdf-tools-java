package io.github.p4suta.register.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.core.OutputFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The runner's filesystem helpers: glob collection, output-dir preparation and path mirroring. */
class RunnerTest {

    @Test
    void collectFilesMatchesTheGlobRecursivelyInPathOrder(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("b.tif"), "");
        Files.writeString(root.resolve("a.png"), "");
        Files.writeString(root.resolve("note.txt"), "");
        Path sub = Files.createDirectory(root.resolve("sub"));
        Files.writeString(sub.resolve("d.png"), "");

        List<Path> matched = Runner.collectFiles(root, "*.{png,tif}");

        assertEquals(
                List.of(root.resolve("a.png"), root.resolve("b.tif"), sub.resolve("d.png")),
                matched);
    }

    @Test
    void prepareOutputDirCreatesAMissingDirectory(@TempDir Path base) throws IOException {
        Path dir = base.resolve("fresh");
        Runner.prepareOutputDir(dir, false);
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void prepareOutputDirRejectsANonEmptyDirectoryWithoutForce(@TempDir Path dir)
            throws IOException {
        Files.writeString(dir.resolve("existing.png"), "");
        assertThrows(IOException.class, () -> Runner.prepareOutputDir(dir, false));
    }

    @Test
    void prepareOutputDirAllowsANonEmptyDirectoryWithForce(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("existing.png"), "");
        assertDoesNotThrow(() -> Runner.prepareOutputDir(dir, true));
    }

    @Test
    void mirrorDestinationMirrorsLayoutAndSwapsExtension() {
        Path src = Path.of("in", "sub", "page.tiff");
        Path dest = Runner.mirrorDestination(src, Path.of("in"), Path.of("out"), OutputFormat.PNG);
        assertEquals(Path.of("out", "sub", "page.png"), dest);
    }

    @Test
    void mirrorDestinationKeepsTheExtensionForFormatSame() {
        Path src = Path.of("in", "sub", "page.tiff");
        Path dest = Runner.mirrorDestination(src, Path.of("in"), Path.of("out"), OutputFormat.SAME);
        assertEquals(Path.of("out", "sub", "page.tiff"), dest);
    }
}
