package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.application.JobReaper;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.ResultCache;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically sweeps expired jobs (dropping each one's SSE buffer) and evicts result-cache entries
 * older than {@code app.cache-ttl}, on the schedule {@code app.reaper-interval-ms}.
 */
@Component
public class ReaperJob {

    private static final Logger log = LoggerFactory.getLogger(ReaperJob.class);

    private final JobReaper reaper;
    private final SseProgressPublisher publisher;
    private final ResultCache cache;
    private final Clock clock;
    private final Duration cacheTtl;

    /**
     * @param reaper removes expired jobs and their files
     * @param publisher holds the SSE buffers to drop alongside
     * @param cache the result cache whose old entries are evicted
     * @param clock the time source
     * @param properties supplies the cache TTL
     */
    public ReaperJob(
            JobReaper reaper,
            SseProgressPublisher publisher,
            ResultCache cache,
            Clock clock,
            WebappProperties properties) {
        this.reaper = reaper;
        this.publisher = publisher;
        this.cache = cache;
        this.clock = clock;
        this.cacheTtl = properties.cacheTtl();
    }

    /** Removes expired jobs and forgets their SSE streams, then evicts old cache entries. */
    @Scheduled(fixedDelayString = "${app.reaper-interval-ms}")
    public void reap() {
        for (JobId id : reaper.reap()) {
            publisher.forget(id);
        }
        try {
            cache.evictOlderThan(clock.instant().minus(cacheTtl));
        } catch (IOException e) {
            log.warn("could not evict expired cache entries: {}", e.getMessage());
        }
    }
}
