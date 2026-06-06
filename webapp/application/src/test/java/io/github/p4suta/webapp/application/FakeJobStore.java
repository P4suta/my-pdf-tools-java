package io.github.p4suta.webapp.application;

import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.JobStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link JobStore} for the use-case tests, preserving insertion order in {@link #all}.
 */
final class FakeJobStore implements JobStore {

    private final Map<JobId, Job> jobs = new LinkedHashMap<>();

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
        return new ArrayList<>(jobs.values());
    }

    @Override
    public void delete(JobId id) {
        jobs.remove(id);
    }
}
