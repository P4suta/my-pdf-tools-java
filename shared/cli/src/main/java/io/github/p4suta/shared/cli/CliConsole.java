package io.github.p4suta.shared.cli;

/**
 * Whether the process is attached to an interactive terminal.
 *
 * <p>Progress renderers use this to decide between a carriage-return animation (a single line
 * rewritten in place) and plain newline-terminated lines: a {@code \r} animation redirected to a
 * file or CI log leaves carriage-return garbage. Since JDK 22 {@code System.console()} returns a
 * non-null {@code Console} even when the streams are redirected, so the test is {@code
 * Console.isTerminal()} rather than a null check.
 */
public final class CliConsole {

    private CliConsole() {}

    /** {@return whether stdin/stdout are attached to an interactive terminal} */
    // SystemConsoleNull: the JDK stub annotates System.console() @Nullable, so NullAway requires
    // the
    // guard even though the runtime no longer returns null — keep it and silence the heuristic.
    @SuppressWarnings("SystemConsoleNull")
    public static boolean isInteractive() {
        java.io.Console console = System.console();
        return console != null && console.isTerminal();
    }
}
