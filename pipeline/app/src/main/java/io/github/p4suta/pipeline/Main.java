package io.github.p4suta.pipeline;

import io.github.p4suta.pipeline.cli.PipelineCommand;
import io.github.p4suta.shared.observability.FatalUncaughtHandler;

/** Process entry point for the unified pipeline tool ({@code pdfbook}). */
public final class Main {

    private Main() {}

    /**
     * Installs the fatal uncaught-exception handler, runs the CLI, and translates the result into a
     * process exit code. All argument parsing and terminal output live in {@link PipelineCommand}.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
        System.exit(new PipelineCommand().run(args));
    }
}
