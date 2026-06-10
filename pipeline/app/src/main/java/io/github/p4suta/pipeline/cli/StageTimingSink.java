package io.github.p4suta.pipeline.cli;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Measures each stage's wall clock from its {@link ProgressEvent.StageStarted}/{@link
 * ProgressEvent.StageCompleted} boundaries and prints a per-stage breakdown when the run ends — the
 * {@code --timings} flag's implementation. One line per stage in completion order, then the
 * run-wide total:
 *
 * <pre>{@code
 * timing: extract = 4.21s (18.3%)
 * timing: despeckle = 9.87s (42.9%)
 * timing: total = 23.01s
 * }</pre>
 *
 * <p>The {@code timing: <stage> = <seconds>s} shape is a stable contract the {@code benchPipeline}
 * harness parses; keep it machine-readable. A stage still open when the run fails is reported with
 * its elapsed-so-far, so a failed run still shows where the time went. Thread-safe like every
 * {@link ProgressSink}: events are handled under one lock.
 */
final class StageTimingSink implements ProgressSink {

    private final PrintStream out;
    private final Object lock = new Object();
    private final List<String> stages = new ArrayList<>();
    private final List<Long> stageNanos = new ArrayList<>();
    private @Nullable String openStage;
    private long openedAtNanos;
    private long runStartedAtNanos;
    private boolean runStarted;

    StageTimingSink(PrintStream out) {
        this.out = out;
    }

    @Override
    public void emit(ProgressEvent event) {
        synchronized (lock) {
            switch (event) {
                case ProgressEvent.RunStarted ignored -> markRunStarted();
                case ProgressEvent.StageStarted s -> {
                    // Defensive: a sink wired mid-run still measures from the first boundary.
                    markRunStarted();
                    openStage = s.stage();
                    openedAtNanos = System.nanoTime();
                }
                case ProgressEvent.StageCompleted ignored -> closeOpenStage();
                case ProgressEvent.PageProcessed ignored -> {
                    // Stage boundaries carry all the timing information.
                }
                case ProgressEvent.RunCompleted ignored -> report();
                case ProgressEvent.RunFailed ignored -> report();
            }
        }
    }

    private void markRunStarted() {
        if (!runStarted) {
            runStartedAtNanos = System.nanoTime();
            runStarted = true;
        }
    }

    private void closeOpenStage() {
        @Nullable String stage = openStage;
        if (stage != null) {
            stages.add(stage);
            stageNanos.add(System.nanoTime() - openedAtNanos);
            openStage = null;
        }
    }

    private void report() {
        closeOpenStage();
        long totalNanos = runStarted ? System.nanoTime() - runStartedAtNanos : 0;
        for (int i = 0; i < stages.size(); i++) {
            out.printf(
                    Locale.ROOT,
                    "timing: %s = %.2fs (%.1f%%)%n",
                    stages.get(i),
                    stageNanos.get(i) / 1e9,
                    totalNanos > 0 ? stageNanos.get(i) * 100.0 / totalNanos : 0.0);
        }
        out.printf(Locale.ROOT, "timing: total = %.2fs%n", totalNanos / 1e9);
    }
}
