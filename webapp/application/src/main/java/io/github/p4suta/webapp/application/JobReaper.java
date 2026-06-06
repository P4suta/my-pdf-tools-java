package io.github.p4suta.webapp.application;

import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.JobStore;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes jobs whose age exceeds a time-to-live, deleting each one's workspace. Reaping by {@code
 * createdAt} sweeps both finished jobs whose results were never downloaded and any orphans left
 * behind by a crash; with a TTL of hours, an in-flight conversion (which takes minutes) is never
 * caught.
 */
public final class JobReaper {

    private static final Logger log = LoggerFactory.getLogger(JobReaper.class);

    private final JobStore store;
    private final Workspace workspace;
    private final Clock clock;
    private final Duration ttl;

    /**
     * @param store the job store
     * @param workspace owns each job's files
     * @param clock the time source
     * @param ttl how long a job may live after creation before it is removed
     */
    public JobReaper(JobStore store, Workspace workspace, Clock clock, Duration ttl) {
        this.store = store;
        this.workspace = workspace;
        this.clock = clock;
        this.ttl = ttl;
    }

    /**
     * Removes every job older than the TTL.
     *
     * @return the ids of the jobs removed, so the caller can drop their auxiliary state (e.g. an
     *     SSE buffer) too
     */
    public List<JobId> reap() {
        Instant cutoff = clock.instant().minus(ttl);
        List<JobId> removed = new ArrayList<>();
        for (Job job : store.all()) {
            if (job.createdAt().isBefore(cutoff)) {
                store.delete(job.id());
                removeQuietly(job.id());
                removed.add(job.id());
            }
        }
        if (!removed.isEmpty()) {
            log.info("reaped {} expired job(s)", removed.size());
        }
        return List.copyOf(removed);
    }

    private void removeQuietly(JobId id) {
        try {
            workspace.remove(id);
        } catch (IOException e) {
            log.warn("could not clean up workspace for {}: {}", id.value(), e.getMessage());
        }
    }
}
