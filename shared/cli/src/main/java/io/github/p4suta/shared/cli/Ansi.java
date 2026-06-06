package io.github.p4suta.shared.cli;

/**
 * Minimal ANSI styling that no-ops when output is not a terminal, so the same call site produces
 * colored text on a TTY and plain text in a pipe / file / CI log. Construct with {@link
 * #forTerminal()} (auto-detects) or {@code new Ansi(boolean)} in tests.
 */
public final class Ansi {

    // ESC built from its code point so no raw escape byte sits in the source.
    private static final String ESC = String.valueOf((char) 27) + "[";
    private static final String RESET = ESC + "0m";

    private final boolean enabled;

    /**
     * @param enabled whether to emit escape codes
     */
    public Ansi(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@return an {@code Ansi} enabled only when stdout/stderr are an interactive terminal} */
    public static Ansi forTerminal() {
        return new Ansi(CliConsole.isInteractive());
    }

    /** {@return whether styling is enabled} */
    public boolean enabled() {
        return enabled;
    }

    private String wrap(String code, String text) {
        return enabled ? ESC + code + "m" + text + RESET : text;
    }

    /** {@return {@code text} in bold (or unchanged when disabled)} */
    public String bold(String text) {
        return wrap("1", text);
    }

    /** {@return {@code text} dimmed (or unchanged when disabled)} */
    public String dim(String text) {
        return wrap("2", text);
    }

    /** {@return {@code text} in red (or unchanged when disabled)} */
    public String red(String text) {
        return wrap("31", text);
    }

    /** {@return {@code text} in green (or unchanged when disabled)} */
    public String green(String text) {
        return wrap("32", text);
    }

    /** {@return {@code text} in yellow (or unchanged when disabled)} */
    public String yellow(String text) {
        return wrap("33", text);
    }

    /** {@return {@code text} in cyan (or unchanged when disabled)} */
    public String cyan(String text) {
        return wrap("36", text);
    }
}
