package io.github.p4suta.shared.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Output-directory preparation: create the directory when absent, but refuse a non-empty one unless
 * the caller passed {@code force}.
 *
 * <p>The "is not empty; pass {@code --force}" message is asserted verbatim by app tests; do not
 * reword it.
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
