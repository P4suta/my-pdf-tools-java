package io.github.p4suta.pipeline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.progress.ProgressEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code --timings} report: one machine-parseable {@code timing: <stage> = <seconds>s}
 * line per completed stage (in completion order, percentages attached) plus a {@code timing: total}
 * line, printed only when the run ends — and on failure, the still-open stage is reported with its
 * elapsed-so-far. The line shape is the contract the {@code benchPipeline} harness parses.
 */
final class StageTimingSinkTest {

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private final StageTimingSink sink =
            new StageTimingSink(new PrintStream(buf, true, StandardCharsets.UTF_8));

    private String output() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void completedRunReportsEachStageInOrderAndATotal() {
        sink.emit(new ProgressEvent.RunStarted(2));
        sink.emit(new ProgressEvent.StageStarted("extract", 0, 2));
        sink.emit(new ProgressEvent.PageProcessed("extract", 1, 2));
        sink.emit(new ProgressEvent.StageCompleted("extract"));
        sink.emit(new ProgressEvent.StageStarted("spread", 1, 2));
        sink.emit(new ProgressEvent.StageCompleted("spread"));
        sink.emit(new ProgressEvent.RunCompleted());

        assertThat(output().lines())
                .hasSize(3)
                .satisfiesExactly(
                        extract ->
                                assertThat(extract)
                                        .matches(
                                                "timing: extract = \\d+\\.\\d{2}s"
                                                        + " \\(\\d+\\.\\d%\\)"),
                        spread ->
                                assertThat(spread)
                                        .matches(
                                                "timing: spread = \\d+\\.\\d{2}s"
                                                        + " \\(\\d+\\.\\d%\\)"),
                        total -> assertThat(total).matches("timing: total = \\d+\\.\\d{2}s"));
    }

    @Test
    void nothingIsPrintedBeforeTheRunEnds() {
        sink.emit(new ProgressEvent.RunStarted(1));
        sink.emit(new ProgressEvent.StageStarted("extract", 0, 1));
        sink.emit(new ProgressEvent.StageCompleted("extract"));

        assertThat(output()).isEmpty();
    }

    @Test
    void failedRunReportsTheStillOpenStage() {
        sink.emit(new ProgressEvent.RunStarted(2));
        sink.emit(new ProgressEvent.StageStarted("extract", 0, 2));
        sink.emit(new ProgressEvent.StageCompleted("extract"));
        sink.emit(new ProgressEvent.StageStarted("register", 1, 2));
        sink.emit(new ProgressEvent.RunFailed("INTERNAL", "boom"));

        assertThat(output())
                .contains("timing: extract = ")
                .contains("timing: register = ")
                .contains("timing: total = ");
    }
}
