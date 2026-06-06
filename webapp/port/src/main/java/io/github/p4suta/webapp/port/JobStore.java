package io.github.p4suta.webapp.port;

import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import java.util.List;
import java.util.Optional;

/** Persists jobs and their lifecycle state. Implemented in-memory for the single-user tool. */
public interface JobStore {

    /**
     * Inserts or replaces the job under its id.
     *
     * @param job the job to store
     */
    void save(Job job);

    /**
     * Looks a job up by id.
     *
     * @param id the job id
     * @return the job, or empty if none is stored under {@code id}
     */
    Optional<Job> find(JobId id);

    /** {@return every stored job} — used by the reaper to find expired ones. */
    List<Job> all();

    /**
     * Removes the job under {@code id}, if any.
     *
     * @param id the job id
     */
    void delete(JobId id);
}
