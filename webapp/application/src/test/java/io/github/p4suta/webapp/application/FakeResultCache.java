package io.github.p4suta.webapp.application;

import io.github.p4suta.webapp.domain.CacheKey;
import io.github.p4suta.webapp.port.ResultCache;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** An in-memory {@link ResultCache} for tests; stored results are copied under a temp dir. */
final class FakeResultCache implements ResultCache {

    private final Path base;
    private final Map<String, Path> entries = new HashMap<>();

    FakeResultCache(Path base) {
        this.base = base;
    }

    @Override
    public Optional<Path> find(CacheKey key) {
        Path path = entries.get(key.value());
        return path != null && Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    @Override
    public void store(CacheKey key, Path outputPdf) throws IOException {
        entries.put(key.value(), copyIn(key, Files.readString(outputPdf)));
    }

    @Override
    public int evictOlderThan(Instant cutoff) {
        int removed = entries.size();
        entries.clear();
        return removed;
    }

    /** Preseeds a hit: {@code key} resolves to a file holding {@code content}. */
    void seed(CacheKey key, String content) throws IOException {
        entries.put(key.value(), copyIn(key, content));
    }

    private Path copyIn(CacheKey key, String content) throws IOException {
        Files.createDirectories(base);
        Path dest = base.resolve(key.value());
        Files.writeString(dest, content); // default: CREATE + WRITE + TRUNCATE_EXISTING
        return dest;
    }
}
