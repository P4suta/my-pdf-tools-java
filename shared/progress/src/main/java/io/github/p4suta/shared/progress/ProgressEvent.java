package io.github.p4suta.shared.progress;

import io.github.p4suta.shared.kernel.Validators;

/**
 * The single, framework-free vocabulary of progress and lifecycle events emitted by a pdfbook
 * conversion and consumed by any front end (the CLI's {@code --progress-file}, the web layer's SSE
 * stream). One sealed hierarchy so producers and consumers share exactly one shape; the JSONL wire
 * form is {@link JsonlProgressCodec}.
 *
 * <p>Stage identity is a plain {@code String} label (the pipeline's {@code Stage.name()}), not an
 * enum, so adding a processing stage needs no change here. Counts are non-negative.
 */
public sealed interface ProgressEvent {

    /**
     * Emitted once at the start of a run, before any stage.
     *
     * @param stageCount the number of stages that will run, from source extraction to the final
     *     repack
     */
    record RunStarted(int stageCount) implements ProgressEvent {

        /** Validates the component. */
        public RunStarted {
            Validators.requireNonNegative(stageCount, "stageCount");
        }
    }

    /**
     * Emitted when a stage begins.
     *
     * @param stage the stage label
     * @param index the zero-based position of this stage within the run
     * @param stageCount the total number of stages in the run
     */
    record StageStarted(String stage, int index, int stageCount) implements ProgressEvent {

        /** Validates the components. */
        public StageStarted {
            Validators.requireNonNull(stage, "stage");
            Validators.requireNonNegative(index, "index");
            Validators.requireNonNegative(stageCount, "stageCount");
        }
    }

    /**
     * Emitted as a stage processes its pages, carrying the fine-grained per-page progress within
     * the stage.
     *
     * @param stage the stage label
     * @param done the number of pages finished so far in this stage
     * @param total the number of pages this stage will process
     */
    record PageProcessed(String stage, int done, int total) implements ProgressEvent {

        /** Validates the components. */
        public PageProcessed {
            Validators.requireNonNull(stage, "stage");
            Validators.requireNonNegative(done, "done");
            Validators.requireNonNegative(total, "total");
        }
    }

    /**
     * Emitted when a stage finishes.
     *
     * @param stage the stage label
     */
    record StageCompleted(String stage) implements ProgressEvent {

        /** Validates the component. */
        public StageCompleted {
            Validators.requireNonNull(stage, "stage");
        }
    }

    /** Terminal success: the conversion finished and the result is complete. */
    record RunCompleted() implements ProgressEvent {}

    /**
     * Terminal failure: the conversion stopped with an error.
     *
     * @param kind the stable error-kind token (the observability error vocabulary)
     * @param message a human-readable description of the failure
     */
    record RunFailed(String kind, String message) implements ProgressEvent {

        /** Validates the components. */
        public RunFailed {
            Validators.requireNonNull(kind, "kind");
            Validators.requireNonNull(message, "message");
        }
    }
}
