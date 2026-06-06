package io.github.p4suta.shared.progress;

/**
 * A consumer of {@link ProgressEvent}s — the emit side of the progress channel. A pdfbook
 * conversion is handed one sink and reports into it; what the sink does with each event (write a
 * JSONL line to a file, push to an SSE stream, discard) is the implementation's concern.
 *
 * <p>Implementations that may be called from the parallel page workers must be thread-safe; {@link
 * #NO_OP} trivially is.
 */
@FunctionalInterface
public interface ProgressSink {

    /** A sink that silently discards every event — the default when no progress is requested. */
    ProgressSink NO_OP = event -> {};

    /**
     * Reports one event.
     *
     * @param event the event to report
     */
    void emit(ProgressEvent event);
}
