package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.webapp.domain.CacheKey;
import io.github.p4suta.webapp.port.ResultCache;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link ResultCache} on the local filesystem: each entry is {@code <root>/<key>/output.pdf}.
 *
 * <p>An entry is published atomically — the result is linked (or copied) to a temp name in the same
 * directory, then {@link StandardCopyOption#ATOMIC_MOVE atomically moved} into place — so a
 * concurrent {@link #find} only ever sees a complete file. The result is shared with the job
 * workspace through a {@linkplain Files#createLink hard link} when both live on the same filesystem
 * (the common case: the cache root is a sibling of the job work dir): the data is stored once and
 * refcounting lets either side be deleted without harming the other. Across devices it falls back
 * to a copy.
 */
public final class FilesystemResultCache implements ResultCache {

    private static final String RESULT = "output.pdf";

    private final Path root;

    /**
     * @param root the directory holding one subdirectory per cache key
     */
    public FilesystemResultCache(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path entryDir(CacheKey key) {
        // key.value() is a validated [0-9a-f]{64} token, so it cannot escape the root.
        return root.resolve(key.value());
    }

    @Override
    public Optional<Path> find(CacheKey key) {
        Path result = entryDir(key).resolve(RESULT);
        return Files.isRegularFile(result) ? Optional.of(result) : Optional.empty();
    }

    @Override
    public void store(CacheKey key, Path outputPdf) throws IOException {
        Path dir = entryDir(key);
        Files.createDirectories(dir);
        Path result = dir.resolve(RESULT);
        if (Files.isRegularFile(result)) {
            return; // already cached by an earlier (or racing) identical run — idempotent
        }
        Path tmp = dir.resolve(RESULT + ".tmp-" + Long.toHexString(System.nanoTime()));
        try {
            linkOrCopy(outputPdf, tmp);
            try {
                Files.move(tmp, result, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileSystemException e) {
                // A concurrent store won the race and created the result first; that is fine.
                if (!Files.isRegularFile(result)) {
                    throw e;
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public int evictOlderThan(Instant cutoff) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> entries = Files.list(root)) {
            for (Path dir : entries.toList()) {
                Path result = dir.resolve(RESULT);
                if (Files.isRegularFile(result)
                        && Files.getLastModifiedTime(result).toInstant().isBefore(cutoff)) {
                    deleteRecursively(dir);
                    removed++;
                }
            }
        }
        return removed;
    }

    private static void linkOrCopy(Path source, Path dest) throws IOException {
        try {
            Files.createLink(dest, source);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // Links unsupported, or source and dest are on different devices (EXDEV): copy instead.
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
