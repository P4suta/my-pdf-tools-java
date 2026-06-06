package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.JobStore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A concurrent in-memory {@link JobStore}. Jobs are mutated by the conversion worker thread while
 * request threads read them, so the backing map is a {@link ConcurrentHashMap}; jobs themselves are
 * immutable records, so a reader never sees a half-updated job.
 */
public final class InMemoryJobStore implements JobStore {

    private final ConcurrentHashMap<JobId, Job> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(Job job) {
        jobs.put(job.id(), job);
    }

    @Override
    public Optional<Job> find(JobId id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<Job> all() {
        return List.copyOf(jobs.values());
    }

    @Override
    public void delete(JobId id) {
        jobs.remove(id);
    }
}
