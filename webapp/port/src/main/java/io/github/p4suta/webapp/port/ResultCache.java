package io.github.p4suta.webapp.port;

import io.github.p4suta.webapp.domain.CacheKey;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * A content-addressed store of finished result PDFs, keyed by {@link CacheKey}. An identical
 * conversion — same input bytes, same options — reuses a previous run's output instead of invoking
 * pdfbook again. Entries outlive the jobs that produced them (that is the point of the cache); a
 * scheduled sweep evicts old ones.
 */
public interface ResultCache {

    /**
     * {@return the cached result for {@code key} if present, else empty}
     *
     * @param key the content-addressed key
     */
    Optional<Path> find(CacheKey key);

    /**
     * Publishes {@code outputPdf} as the cached result for {@code key}, atomically — a concurrent
     * {@link #find} never observes a half-written entry — and idempotently (a second store for an
     * existing key is a no-op).
     *
     * @param key the content-addressed key
     * @param outputPdf the finished result to cache
     * @throws IOException if the entry cannot be written
     */
    void store(CacheKey key, Path outputPdf) throws IOException;

    /**
     * Removes every entry last written before {@code cutoff}.
     *
     * @param cutoff entries older than this are evicted
     * @return how many entries were removed
     * @throws IOException if the cache cannot be swept
     */
    int evictOlderThan(Instant cutoff) throws IOException;
}
