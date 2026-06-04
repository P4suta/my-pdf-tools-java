package io.github.p4suta.register;

import io.github.p4suta.register.cli.RegisterCommand;
import io.github.p4suta.shared.observability.FatalUncaughtHandler;

/** Process entry point. */
public final class Main {

    private Main() {}

    /**
     * CLI entry point: install the fatal uncaught-exception handler, parse and run, then translate
     * the result into a process exit code. All argument parsing and terminal output live in {@link
     * RegisterCommand}.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new FatalUncaughtHandler());
        System.exit(new RegisterCommand().execute(args));
    }
}
