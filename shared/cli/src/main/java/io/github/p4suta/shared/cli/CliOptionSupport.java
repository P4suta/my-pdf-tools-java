package io.github.p4suta.shared.cli;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.help.TextHelpAppendable;
import org.jspecify.annotations.Nullable;

/**
 * Argument-parsing helpers: int/double/enum value parsing, the positional-count check, and the help
 * / usage-error renderers.
 *
 * <p>The value parsers throw {@link ParseException} on bad input (rather than {@code
 * NumberFormatException}/{@code IllegalArgumentException}), keeping a parse failure on the Commons
 * CLI usage path — {@link #usageError(String, String, String, Throwable)} returns exit code {@code
 * 2} — instead of routing it through the {@code ExceptionMapper}. Enum parsing lists the valid
 * values in its message.
 */
public final class CliOptionSupport {

    private CliOptionSupport() {}

    /**
     * Parses option {@code opt} as an {@code int}, returning {@code fallback} when it is absent.
     *
     * @param cmd the parsed command line
     * @param opt the option name (without the {@code --} prefix)
     * @param fallback the value to return when the option is absent
     * @return the parsed value, or {@code fallback}
     * @throws ParseException if the option is present but not an integer
     */
    public static int parseInt(CommandLine cmd, String opt, int fallback) throws ParseException {
        String value = cmd.getOptionValue(opt);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new ParseException("--" + opt + " must be an integer, but got: '" + value + "'");
        }
    }

    /**
     * Parses option {@code opt} as a {@code double}, returning {@code fallback} when it is absent.
     *
     * @param cmd the parsed command line
     * @param opt the option name (without the {@code --} prefix)
     * @param fallback the value to return when the option is absent
     * @return the parsed value, or {@code fallback}
     * @throws ParseException if the option is present but not a number
     */
    public static double parseDouble(CommandLine cmd, String opt, double fallback)
            throws ParseException {
        String value = cmd.getOptionValue(opt);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            throw new ParseException("--" + opt + " must be a number, but got: '" + value + "'");
        }
    }

    /**
     * Case-insensitive enum lookup (so {@code png}, {@code top_right} work), returning {@code
     * fallback} when {@code value} is {@code null}. On an unknown value the message lists the valid
     * constants (lower-cased).
     *
     * @param <E> the enum type
     * @param type the enum class
     * @param value the raw value, or {@code null} for the fallback
     * @param fallback the value to return when {@code value} is {@code null}
     * @return the resolved constant, or {@code fallback}
     * @throws ParseException if {@code value} is non-null but not a constant of {@code type}
     */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, @Nullable String value, E fallback)
            throws ParseException {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParseException(
                    "invalid value '" + value + "'; valid values are: " + validValues(type));
        }
    }

    /** {@return the lower-cased, comma-joined constant names of {@code type}} */
    public static <E extends Enum<E>> String validValues(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(c -> c.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    /**
     * Requires exactly {@code n} positional arguments, or a {@link ParseException} naming what was
     * expected.
     *
     * @param cmd the parsed command line
     * @param n the required number of positionals
     * @param names the display names of the positionals, in order (e.g. {@code <inputDir>}, {@code
     *     <outputDir>}); used in the error message
     * @return the positionals, in order
     * @throws ParseException if there are not exactly {@code n} positionals
     */
    public static List<String> requireNPositionals(CommandLine cmd, int n, String... names)
            throws ParseException {
        List<String> positionals = cmd.getArgList();
        if (positionals.size() != n) {
            String expected = names.length == 0 ? "" : " " + String.join(" ", names);
            throw new ParseException(
                    "expected exactly "
                            + n
                            + " positional argument"
                            + (n == 1 ? "" : "s")
                            + expected
                            + ", but got "
                            + positionals.size());
        }
        return List.copyOf(positionals);
    }

    /**
     * Renders a command's full help to {@code System.out} via Commons CLI's {@link HelpFormatter}.
     *
     * <p>The {@code HelpFormatter}'s {@link TextHelpAppendable} pads wrapped option descriptions
     * out to the column width, leaving trailing whitespace on many lines and an extra blank line at
     * the end. Render into a buffer, {@linkplain String#stripTrailing() strip} the trailing
     * whitespace off every line, and drop the trailing blank line(s) before emitting. Leading
     * indentation (which carries the option layout) is preserved.
     *
     * @param program the program name used in the could-not-render fallback message
     * @param syntax the usage syntax line
     * @param header the description block printed above the options
     * @param options the option model to render
     */
    public static void printHelp(String program, String syntax, String header, Options options) {
        StringBuilder buffer = new StringBuilder();
        HelpFormatter formatter =
                HelpFormatter.builder()
                        .setHelpAppendable(new TextHelpAppendable(buffer))
                        .setShowSince(false)
                        .get();
        try {
            formatter.printHelp(syntax, header, options, "", false);
        } catch (IOException e) {
            System.err.println(program + ": could not render help: " + e.getMessage());
            return;
        }
        // Right-trim every line; the per-line padding TextHelpAppendable adds is cosmetic noise.
        List<String> lines =
                buffer.toString().lines().map(String::stripTrailing).collect(Collectors.toList());
        // Drop trailing blank line(s) so the help ends on its last content line.
        int end = lines.size();
        while (end > 0 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        System.out.println(String.join(System.lineSeparator(), lines.subList(0, end)));
    }

    /**
     * Prints a usage error to {@code System.err} and returns the usage exit code {@code 2}.
     *
     * @param program the program name prefixed to the error line (e.g. {@code register})
     * @param syntax the usage syntax line
     * @param tryHint the {@code --help} invocation to suggest (e.g. {@code register --help})
     * @param cause the parse / validation failure to report
     * @return the process exit code for a usage error ({@code 2})
     */
    public static int usageError(String program, String syntax, String tryHint, Throwable cause) {
        System.err.println(program + ": " + cause.getMessage());
        System.err.println("usage: " + syntax);
        System.err.println("Try '" + tryHint + "' for more information.");
        return 2;
    }
}
