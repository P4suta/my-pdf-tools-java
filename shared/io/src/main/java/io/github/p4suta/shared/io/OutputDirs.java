package io.github.p4suta.shared.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Output-directory preparation shared by every corpus-walking pipeline: create the directory when
 * absent, but refuse to scribble over a non-empty one unless the caller passed {@code force}.
 *
 * <p>The "is not empty; pass {@code --force}" message is part of the apps' observable contract —
 * register's and despeckle's end-to-end and unit tests assert it verbatim — so it is reproduced
 * here character-for-character and must not be reworded.
 */
public final class OutputDirs {

    private OutputDirs() {}

    /**
     * Prepare {@code dir} for output: create it (and any missing parents) when it does not exist,
     * otherwise leave an empty directory untouched and reject a non-empty one unless {@code force}.
     *
     * @param dir the output directory to prepare
     * @param force whether to tolerate (overwrite into) a directory that already has entries
     * @throws IOException if {@code dir} is a non-empty directory and {@code force} is {@code
     *     false}, or if directory creation or listing fails
     */
    public static void prepare(Path dir, boolean force) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> entries = Files.list(dir)) {
                if (entries.findAny().isPresent() && !force) {
                    throw new IOException(
                            "output directory " + dir + " is not empty; pass --force to overwrite");
                }
            }
        } else {
            Files.createDirectories(dir);
        }
    }
}
