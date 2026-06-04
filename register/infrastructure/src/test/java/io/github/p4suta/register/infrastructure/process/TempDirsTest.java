package io.github.p4suta.register.infrastructure.process;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempDirsTest {

    @Test
    void createBesidePlacesTheDirUnderTheSiblingsParent(@TempDir Path base) throws IOException {
        // The "sibling" need not exist; only its parent (base) is used.
        Path created = TempDirs.createBeside(base.resolve("output"), ".scratch-");

        assertTrue(Files.isDirectory(created));
        assertTrue(Files.isSameFile(base, created.getParent()));
        assertTrue(created.getFileName().toString().startsWith(".scratch-"));
    }

    @Test
    void deleteRecursivelyRemovesTheWholeTree(@TempDir Path base) throws IOException {
        Path dir = Files.createDirectory(base.resolve("tree"));
        Files.writeString(dir.resolve("a.txt"), "a");
        Path sub = Files.createDirectory(dir.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "b");

        TempDirs.deleteRecursively(dir);

        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteRecursivelyToleratesAMissingDirectory(@TempDir Path base) {
        // Best-effort cleanup must never throw, even when the directory is already gone.
        assertDoesNotThrow(() -> TempDirs.deleteRecursively(base.resolve("does-not-exist")));
    }
}
