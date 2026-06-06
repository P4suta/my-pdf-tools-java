package io.github.p4suta.webapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.CacheKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemResultCacheTest {

    private static final CacheKey KEY = new CacheKey("a".repeat(64));

    @TempDir Path tmp;
    private FilesystemResultCache cache;

    @BeforeEach
    void setUp() {
        cache = new FilesystemResultCache(tmp.resolve("cache"));
    }

    @Test
    void findIsEmptyUntilStored() {
        assertThat(cache.find(KEY)).isEmpty();
    }

    @Test
    void storesAndFindsAResult() throws IOException {
        Path output = Files.writeString(tmp.resolve("out.pdf"), "%PDF book");

        cache.store(KEY, output);

        assertThat(cache.find(KEY)).isPresent();
        assertThat(Files.readString(cache.find(KEY).orElseThrow())).isEqualTo("%PDF book");
    }

    @Test
    void storeIsIdempotent() throws IOException {
        Path output = Files.writeString(tmp.resolve("out.pdf"), "%PDF book");
        cache.store(KEY, output);
        cache.store(KEY, output); // a second store for the same key is a no-op, not an error

        assertThat(cache.find(KEY)).isPresent();
    }

    @Test
    void evictsEntriesOlderThanTheCutoff() throws IOException {
        Path output = Files.writeString(tmp.resolve("out.pdf"), "%PDF book");
        cache.store(KEY, output);
        Path result = cache.find(KEY).orElseThrow();
        Files.setLastModifiedTime(result, FileTime.from(Instant.parse("2000-01-01T00:00:00Z")));

        int removed = cache.evictOlderThan(Instant.parse("2001-01-01T00:00:00Z"));

        assertThat(removed).isEqualTo(1);
        assertThat(cache.find(KEY)).isEmpty();
    }

    @Test
    void keepsEntriesNewerThanTheCutoff() throws IOException {
        Path output = Files.writeString(tmp.resolve("out.pdf"), "%PDF book");
        cache.store(KEY, output);

        int removed = cache.evictOlderThan(Instant.parse("2000-01-01T00:00:00Z"));

        assertThat(removed).isZero();
        assertThat(cache.find(KEY)).isPresent();
    }
}
