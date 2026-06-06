package io.github.p4suta.register.infrastructure.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scratch-directory helpers for the infrastructure layer: create a working directory beside an
 * output and remove a directory tree when a run ends. Used by the diagnostics flip-book to manage
 * its frame scratch directory; the application orchestration keeps its own equivalents inlined so
 * it needs no dependency on this layer.
 */
public final class TempDirs {

    private static final Logger LOG = LoggerFactory.getLogger(TempDirs.class);

    private TempDirs() {}

    /**
     * Create a temporary directory <em>beside</em> {@code sibling} — under its parent, so the
     * scratch space shares a filesystem with the output it serves (keeping moves/links cheap) and
     * is cleaned up in the same tree. Falls back to the working directory when {@code sibling} has
     * no usable parent.
     *
     * @throws IOException if the directory cannot be created
     */
    public static Path createBeside(Path sibling, String prefix) throws IOException {
        Path parent = sibling.toAbsolutePath().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            return Files.createTempDirectory(parent, prefix);
        }
        return Files.createTempDirectory(prefix);
    }

    /**
     * Delete {@code dir} and everything under it, best-effort: walked depth-first (children before
     * parents) so directories are empty when removed. A failure to delete any single entry is
     * logged and skipped rather than thrown — cleanup must never mask the run's real outcome; the
     * caller's surrounding log lines supply the context.
     */
    public static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(TempDirs::deleteQuietly);
        } catch (IOException e) {
            LOG.warn("could not clean up temp directory {}: {}", dir, e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("could not delete {}: {}", path, e.getMessage());
        }
    }
}
