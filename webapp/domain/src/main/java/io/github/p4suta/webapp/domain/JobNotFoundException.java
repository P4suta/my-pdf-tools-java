package io.github.p4suta.webapp.domain;

/** Thrown by the use cases when a {@link JobId} does not correspond to any known job. */
public final class JobNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param id the unknown job id
     */
    public JobNotFoundException(JobId id) {
        super("no such job: " + id.value());
    }
}
