package io.github.p4suta.shared.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * A line-based interactive prompt over injected streams — the testable seam for a guided CLI flow:
 * production wires it to the console, tests feed scripted answers through a plain {@link
 * BufferedReader}/{@link PrintStream}. It owns no {@code System.console()} call (see {@link
 * #console()}), so the wizard logic is unit-testable end to end.
 *
 * <p>An empty answer takes the offered default; a missing default re-prompts. End-of-input (the
 * stream closes mid-prompt) is a hard stop, surfaced as {@link UncheckedIOException} so a
 * non-interactive {@code -i} run fails fast rather than looping on {@code null} reads.
 */
public final class Prompt {

    private final BufferedReader in;
    private final PrintStream out;
    private final Ansi ansi;

    /**
     * @param in the answer source
     * @param out where prompts are written
     * @param ansi the styling helper (disabled renders plain text)
     */
    public Prompt(BufferedReader in, PrintStream out, Ansi ansi) {
        this.in = in;
        this.out = out;
        this.ansi = ansi;
    }

    /**
     * {@return a {@code Prompt} bound to the process console, styled for the terminal}
     *
     * @throws IllegalStateException if there is no interactive console (piped / non-TTY): an
     *     interactive flow cannot run, and must fail fast rather than block
     */
    public static Prompt console() {
        if (!CliConsole.isInteractive()) {
            throw new IllegalStateException(
                    "interactive mode needs a terminal (stdin/stdout are not a TTY)");
        }
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return new Prompt(reader, System.out, Ansi.forTerminal());
    }

    /**
     * Asks {@code question}, returning the typed line or {@code def} on an empty answer; re-prompts
     * while the answer is empty and {@code def} is {@code null}.
     *
     * @param question the prompt text
     * @param def the default shown in brackets and returned on an empty answer, or {@code null} to
     *     require a non-empty answer
     * @return the answer
     */
    public String ask(String question, @Nullable String def) {
        while (true) {
            out.print(ansi.bold(question) + suffix(def) + " ");
            out.flush();
            String line = readReply().strip();
            if (!line.isEmpty()) {
                return line;
            }
            if (def != null) {
                return def;
            }
            out.println(ansi.yellow("  (a value is required)"));
        }
    }

    /**
     * Asks a yes/no {@code question}.
     *
     * @param question the prompt text
     * @param def the value returned on an empty answer
     * @return the choice
     */
    public boolean confirm(String question, boolean def) {
        String hint = def ? "[Y/n]" : "[y/N]";
        while (true) {
            out.print(ansi.bold(question) + " " + hint + " ");
            out.flush();
            String line = readReply().strip().toLowerCase(Locale.ROOT);
            if (line.isEmpty()) {
                return def;
            }
            if (line.equals("y") || line.equals("yes")) {
                return true;
            }
            if (line.equals("n") || line.equals("no")) {
                return false;
            }
            out.println(ansi.yellow("  (please answer y or n)"));
        }
    }

    /**
     * Asks {@code question} as a numbered menu, returning the chosen item's value.
     *
     * @param <T> the value type
     * @param question the prompt text
     * @param choices the options, in display order
     * @param def the default option (must be one of {@code choices}); chosen on an empty answer
     * @return the selected value
     */
    public <T> T select(String question, List<Choice<T>> choices, Choice<T> def) {
        out.println(ansi.bold(question));
        for (int i = 0; i < choices.size(); i++) {
            Choice<T> c = choices.get(i);
            String marker = c.equals(def) ? ansi.dim(" (default)") : "";
            out.println("  " + ansi.cyan(String.valueOf(i + 1)) + ") " + c.label() + marker);
        }
        while (true) {
            out.print("  > ");
            out.flush();
            String line = readReply().strip();
            if (line.isEmpty()) {
                return def.value();
            }
            try {
                int n = Integer.parseInt(line);
                if (n >= 1 && n <= choices.size()) {
                    return choices.get(n - 1).value();
                }
            } catch (NumberFormatException ignored) {
                // fall through to the re-prompt
            }
            out.println(ansi.yellow("  (enter a number from 1 to " + choices.size() + ")"));
        }
    }

    /** Prints {@code line} to the prompt stream (e.g. a heading between question groups). */
    public void say(String line) {
        out.println(line);
    }

    private String suffix(@Nullable String def) {
        return def == null ? "" : ansi.dim(" [" + def + "]");
    }

    private String readReply() {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new UncheckedIOException(
                        new IOException("input closed before the prompt was answered"));
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One labeled menu option.
     *
     * @param <T> the value type
     * @param label the text shown in the menu
     * @param value the value returned when chosen
     */
    public record Choice<T>(String label, T value) {

        /**
         * {@return a choice labeled {@code label} carrying {@code value}}
         *
         * @param <T> the value type
         * @param label the menu text
         * @param value the value returned when chosen
         */
        public static <T> Choice<T> of(String label, T value) {
            return new Choice<>(label, value);
        }
    }
}
