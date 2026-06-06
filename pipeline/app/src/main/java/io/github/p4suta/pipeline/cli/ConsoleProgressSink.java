package io.github.p4suta.pipeline.cli;

import io.github.p4suta.shared.cli.Ansi;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.PrintStream;

/**
 * Renders pdfbook progress events to a stream as a live, single-line bar — the interactive ({@code
 * -i}) front end's progress display. On a terminal each stage's bar is rewritten in place with a
 * carriage return; when not interactive the per-page updates are suppressed and only stage
 * boundaries print, so a redirected log stays clean. Thread-safe: the parallel page workers all
 * emit here, so every event is handled under one lock.
 */
final class ConsoleProgressSink implements ProgressSink {

    private static final int BAR_WIDTH = 24;

    private final PrintStream out;
    private final Ansi ansi;
    private final boolean interactive;
    private final Object lock = new Object();

    private boolean barOpen; // a \r bar line is pending a terminating newline

    ConsoleProgressSink(PrintStream out, Ansi ansi, boolean interactive) {
        this.out = out;
        this.ansi = ansi;
        this.interactive = interactive;
    }

    /** {@return a sink writing to {@code System.err}, styled and animated only on a terminal} */
    static ConsoleProgressSink forTerminal() {
        Ansi ansi = Ansi.forTerminal();
        return new ConsoleProgressSink(System.err, ansi, ansi.enabled());
    }

    @Override
    public void emit(ProgressEvent event) {
        synchronized (lock) {
            switch (event) {
                case ProgressEvent.StageStarted s -> {
                    finishBar();
                    out.println(
                            ansi.cyan("[" + (s.index() + 1) + "/" + s.stageCount() + "] ")
                                    + s.stage());
                }
                case ProgressEvent.PageProcessed p -> {
                    if (interactive) {
                        out.print(
                                "\r  "
                                        + bar(p.done(), p.total())
                                        + " "
                                        + p.done()
                                        + "/"
                                        + p.total());
                        out.flush();
                        barOpen = true;
                    }
                }
                case ProgressEvent.StageCompleted ignored -> finishBar();
                case ProgressEvent.RunCompleted ignored -> {
                    finishBar();
                    out.println(ansi.green("✓ done"));
                }
                case ProgressEvent.RunFailed f -> {
                    finishBar();
                    out.println(ansi.red("✗ " + f.kind()));
                }
                case ProgressEvent.RunStarted ignored -> {
                    // The first StageStarted carries the count; nothing to show yet.
                }
            }
        }
    }

    private void finishBar() {
        if (barOpen) {
            out.println();
            barOpen = false;
        }
    }

    private String bar(int done, int total) {
        int filled = total <= 0 ? 0 : Math.min(BAR_WIDTH, (int) ((long) done * BAR_WIDTH / total));
        return "[" + "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled) + "]";
    }
}
