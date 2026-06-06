package io.github.p4suta.despeckle.domain.model;

/** The outcome of one book in a {@code despeckle pipeline} batch. */
public enum BookStatus {
    /** The book was cleaned this run. */
    OK,
    /** The book's output already existed, so it was skipped (no {@code force}). */
    SKIPPED,
    /** The book threw and was counted but not fatal to the batch. */
    FAILED
}
