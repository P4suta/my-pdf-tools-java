package io.github.p4suta.webapp.domain;

/** Thrown when a job's result is requested before the job has reached {@link JobState#DONE}. */
public final class ResultNotReadyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param id the job whose result was requested
     * @param state the job's current (non-DONE) state
     */
    public ResultNotReadyException(JobId id, JobState state) {
        super("job " + id.value() + " is " + state + ", not yet DONE");
    }
}
