package io.github.p4suta.webapp.domain;

/** The lifecycle state of a conversion job: {@code QUEUED -> RUNNING -> DONE | FAILED}. */
public enum JobState {
    /** Accepted, waiting for a worker. */
    QUEUED,
    /** A worker is converting. */
    RUNNING,
    /** Finished successfully; the result is downloadable. */
    DONE,
    /** Finished with an error. */
    FAILED;

    /** {@return whether this is a terminal state from which no further transition is legal} */
    public boolean isTerminal() {
        return this == DONE || this == FAILED;
    }
}
