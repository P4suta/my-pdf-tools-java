package io.github.p4suta.webapp.port;

/**
 * A read-only snapshot of the conversion executor's queue and worker state, for telemetry. Kept
 * separate from {@link ConversionExecutor} (which only submits work) so a reader that just wants
 * the numbers — the web layer's metrics binder and health checks — does not depend on the executor.
 * Every value is a plain primitive; no framework type crosses this boundary.
 */
public interface QueueStats {

    /** {@return the number of tasks waiting in the queue right now} */
    int queued();

    /** {@return the number of tasks executing right now (0 or 1 for the single-worker pool)} */
    int active();

    /** {@return the queue's total capacity} */
    int capacity();

    /** {@return the number of free slots remaining in the queue} */
    int remainingCapacity();

    /** {@return the total number of tasks that have finished executing since startup} */
    long completed();
}
