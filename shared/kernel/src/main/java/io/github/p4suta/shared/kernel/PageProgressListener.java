package io.github.p4suta.shared.kernel;

/**
 * A minimal per-item progress callback for the corpus-processing services. Each app's service (e.g.
 * despeckle, register) invokes {@link #onPage} once per page (or per spread) as the unit completes,
 * so a caller can render fine-grained progress instead of jumping from 0% to 100% at the stage
 * boundary.
 *
 * <p>This is deliberately a framework-free primitive of the kernel: it knows nothing about stage
 * names or the pipeline/web {@code ProgressEvent} vocabulary. The composing layer (the pipeline's
 * stages) bridges {@code onPage} into its richer event type, so the services stay decoupled from
 * that vocabulary.
 *
 * <p>The services process pages on a worker pool, so an implementation that does real work must be
 * thread-safe; {@link #NO_OP} trivially is.
 */
@FunctionalInterface
public interface PageProgressListener {

    /** A listener that ignores every callback — the default when no progress is requested. */
    PageProgressListener NO_OP = (done, total) -> {};

    /**
     * Reports that one more unit has finished.
     *
     * @param done the running count of finished units, 1-based and monotonically increasing toward
     *     {@code total}
     * @param total the total number of units in the run
     */
    void onPage(int done, int total);
}
