package io.github.p4suta.tateyokopdf;

import io.github.p4suta.shared.observability.FatalUncaughtHandler;
import io.github.p4suta.tateyokopdf.cli.SpreadCommand;

/** Process entry point: installs the fatal-error handler, then hands off to the CLI. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        // Route every uncaught throwable (including OutOfMemoryError on background threads)
        // through a single handler so the process exits with a meaningful code instead of a
        // bare stack trace. Logging goes to stderr only (slf4j-simple's default sink).
        Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
        SpreadCommand.runCli(args);
    }
}
