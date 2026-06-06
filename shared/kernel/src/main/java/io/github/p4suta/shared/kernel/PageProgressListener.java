package io.github.p4suta.shared.kernel;

/**
 * A framework-free per-item progress callback: a corpus-processing service invokes {@link #onPage}
 * once per page (or spread) as it completes. The composing layer (the pipeline's stages) bridges it
 * into the richer {@code ProgressEvent} vocabulary, so services stay decoupled from that.
 *
 * <p>Services process pages on a worker pool, so a real implementation must be thread-safe; {@link
 * #NO_OP} trivially is.
 */
@FunctionalInterface
public interface PageProgressListener {

    /** Ignores every callback — the default when no progress is requested. */
    PageProgressListener NO_OP = (done, total) -> {};

    /**
     * Reports that one more unit has finished.
     *
     * @param done finished-unit count, 1-based and monotonically increasing toward {@code total}
     * @param total the total number of units in the run
     */
    void onPage(int done, int total);
}
