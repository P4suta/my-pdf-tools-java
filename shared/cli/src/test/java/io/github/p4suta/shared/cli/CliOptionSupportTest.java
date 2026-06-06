package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

final class CliOptionSupportTest {

    /** A scanner color mode, used to exercise the case-insensitive enum parser. */
    private enum Mode {
        RGB,
        GRAY,
        BITONAL
    }

    private static Options options() {
        Options options = new Options();
        options.addOption(Option.builder().longOpt("dpi").hasArg().get());
        options.addOption(Option.builder().longOpt("ratio").hasArg().get());
        options.addOption(Option.builder().longOpt("mode").hasArg().get());
        return options;
    }

    private static CommandLine parse(String... args) throws ParseException {
        return new DefaultParser().parse(options(), args);
    }

    // parseInt

    @Test
    void parseIntReturnsFallbackWhenAbsent() throws Exception {
        assertThat(CliOptionSupport.parseInt(parse(), "dpi", 300)).isEqualTo(300);
    }

    @Test
    void parseIntReadsAndTrimsTheValue() throws Exception {
        assertThat(CliOptionSupport.parseInt(parse("--dpi", " 600 "), "dpi", 300)).isEqualTo(600);
    }

    @Test
    void parseIntRejectsNonInteger() throws Exception {
        CommandLine cmd = parse("--dpi", "x");
        assertThatThrownBy(() -> CliOptionSupport.parseInt(cmd, "dpi", 300))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("--dpi")
                .hasMessageContaining("integer");
    }

    // parseDouble

    @Test
    void parseDoubleReturnsFallbackWhenAbsent() throws Exception {
        assertThat(CliOptionSupport.parseDouble(parse(), "ratio", 0.5)).isEqualTo(0.5);
    }

    @Test
    void parseDoubleReadsAndTrimsTheValue() throws Exception {
        assertThat(CliOptionSupport.parseDouble(parse("--ratio", " 0.75 "), "ratio", 0.5))
                .isEqualTo(0.75);
    }

    @Test
    void parseDoubleRejectsNonNumber() throws Exception {
        CommandLine cmd = parse("--ratio", "nope");
        assertThatThrownBy(() -> CliOptionSupport.parseDouble(cmd, "ratio", 0.5))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("--ratio")
                .hasMessageContaining("number");
    }

    // parseEnum

    @Test
    void parseEnumReturnsFallbackWhenNull() throws Exception {
        assertThat(CliOptionSupport.parseEnum(Mode.class, null, Mode.RGB)).isEqualTo(Mode.RGB);
    }

    @Test
    void parseEnumIsCaseInsensitiveAndTrims() throws Exception {
        assertThat(CliOptionSupport.parseEnum(Mode.class, " bitonal ", Mode.RGB))
                .isEqualTo(Mode.BITONAL);
    }

    @Test
    void parseEnumListsValidValuesOnUnknown() {
        assertThatThrownBy(() -> CliOptionSupport.parseEnum(Mode.class, "cmyk", Mode.RGB))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("invalid value 'cmyk'")
                .hasMessageContaining("rgb, gray, bitonal");
    }

    @Test
    void validValuesJoinsLowerCasedConstants() {
        assertThat(CliOptionSupport.validValues(Mode.class)).isEqualTo("rgb, gray, bitonal");
    }

    // requireNPositionals

    @Test
    void requireNPositionalsReturnsThemWhenCountMatches() throws Exception {
        CommandLine cmd = parse("in", "out");
        List<String> positionals = CliOptionSupport.requireNPositionals(cmd, 2, "<in>", "<out>");
        assertThat(positionals).containsExactly("in", "out");
    }

    @Test
    void requireNPositionalsRejectsWrongCountWithNamesInMessage() throws Exception {
        CommandLine cmd = parse("only-one");
        assertThatThrownBy(() -> CliOptionSupport.requireNPositionals(cmd, 2, "<in>", "<out>"))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("expected exactly 2 positional arguments <in> <out>")
                .hasMessageContaining("got 1");
    }

    @Test
    void requireNPositionalsHandlesSingularWording() throws Exception {
        CommandLine cmd = parse("a", "b");
        assertThatThrownBy(() -> CliOptionSupport.requireNPositionals(cmd, 1, "<file>"))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("expected exactly 1 positional argument <file>");
    }

    // printHelp / usageError (touch the real System streams)

    @Test
    @ResourceLock(value = Resources.SYSTEM_OUT, mode = ResourceAccessMode.READ_WRITE)
    void printHelpRendersSyntaxAndHeaderToStdout() {
        PrintStream original = System.out;
        var captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            CliOptionSupport.printHelp(
                    "tool", "tool <in> <out> [options]", "Do the thing.", options());
        } finally {
            System.setOut(original);
        }
        String out = captured.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("tool <in> <out>").contains("Do the thing.").contains("--dpi");
    }

    /**
     * Uses an option whose description is long enough that Commons CLI's {@code TextHelpAppendable}
     * wraps it across columns, which pads continuation lines to the column width (trailing spaces)
     * and appends an extra blank line. A description-free option model would not trigger that
     * padding, so the assertions below would pass vacuously.
     */
    @Test
    @ResourceLock(value = Resources.SYSTEM_OUT, mode = ResourceAccessMode.READ_WRITE)
    void printHelpStripsTrailingWhitespaceAndHasNoExtraTrailingBlankLine() {
        Options wrapping = new Options();
        wrapping.addOption(
                Option.builder()
                        .longOpt("threshold")
                        .hasArg()
                        .desc(
                                "A deliberately long description that must wrap across several"
                                    + " lines so the help formatter pads the continuation rows out"
                                    + " to the column width, exposing the trailing whitespace this"
                                    + " test pins.")
                        .get());

        PrintStream original = System.out;
        var captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            CliOptionSupport.printHelp(
                    "tool", "tool <in> <out> [options]", "Do the thing.", wrapping);
        } finally {
            System.setOut(original);
        }
        String out = captured.toString(StandardCharsets.UTF_8);
        // Sanity: the description really did wrap, so this case is non-vacuous.
        assertThat(out.lines().count()).isGreaterThan(3);
        // No emitted line carries trailing whitespace (printHelp stripTrailing's each line) ...
        assertThat(out.lines())
                .allSatisfy(line -> assertThat(line).isEqualTo(line.stripTrailing()));
        // ... and the help ends on its last content line: exactly one terminating newline, with no
        // extra trailing blank line.
        assertThat(out).endsWith(System.lineSeparator());
        assertThat(out).doesNotEndWith(System.lineSeparator() + System.lineSeparator());
    }

    @Test
    @ResourceLock(value = Resources.SYSTEM_ERR, mode = ResourceAccessMode.READ_WRITE)
    void usageErrorPrintsProgramSyntaxAndHintThenReturnsTwo() {
        PrintStream original = System.err;
        var captured = new ByteArrayOutputStream();
        int code;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            code =
                    CliOptionSupport.usageError(
                            "tool",
                            "tool <in> <out>",
                            "tool --help",
                            new ParseException("bad arg"));
        } finally {
            System.setErr(original);
        }
        assertThat(code).isEqualTo(2);
        String err = captured.toString(StandardCharsets.UTF_8);
        assertThat(err)
                .contains("tool: bad arg")
                .contains("usage: tool <in> <out>")
                .contains("Try 'tool --help' for more information.");
    }
}
