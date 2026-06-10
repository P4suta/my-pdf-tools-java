package io.github.p4suta.pipeline;

import io.github.p4suta.pipeline.cli.PipelineCommand;
import io.github.p4suta.shared.observability.FatalUncaughtHandler;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Process entry point for the unified pipeline tool ({@code pdfbook}). */
public final class Main {

    /**
     * How long the shutdown hook waits for the interrupted run to unwind. Generous: the page
     * fan-outs quiesce in at most one page's worth of native work per worker, and the extractor
     * kills its children on interrupt.
     */
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(15);

    private Main() {}

    /**
     * Installs the fatal uncaught-exception handler and the Ctrl-C cleanup hook, runs the CLI, and
     * translates the result into a process exit code. All argument parsing and terminal output live
     * in {@link PipelineCommand}.
     *
     * <p>On Ctrl-C (or any normal-termination signal) the shutdown hook interrupts the main thread
     * and waits — bounded by {@link #SHUTDOWN_GRACE} — for the run to unwind: the page fan-outs
     * stop their workers and quiesce, subprocesses are killed, and the run's {@code finally} blocks
     * delete the temp work area. A Ctrl-C therefore no longer leaks a {@code p4suta-pipeline-}
     * directory full of intermediates. On a normal exit the latch is already down, so the hook
     * returns immediately.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
        CountDownLatch unwound = new CountDownLatch(1);
        Thread main = Thread.currentThread();
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    main.interrupt();
                                    try {
                                        boolean clean =
                                                unwound.await(
                                                        SHUTDOWN_GRACE.toMillis(),
                                                        TimeUnit.MILLISECONDS);
                                        if (!clean) {
                                            System.err.println(
                                                    "pdfbook: shutdown grace expired; temp files"
                                                            + " may remain");
                                        }
                                    } catch (InterruptedException ignored) {
                                        // The JVM is exiting anyway; stop waiting.
                                    }
                                },
                                "pdfbook-shutdown"));
        int code = new PipelineCommand().run(args);
        unwound.countDown();
        System.exit(code);
    }
}
