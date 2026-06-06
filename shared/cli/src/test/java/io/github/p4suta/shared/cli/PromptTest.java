package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The injected-IO prompt: defaults, re-prompts, menu selection, and fail-fast on closed input. */
final class PromptTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private Prompt promptFeeding(String input) {
        return new Prompt(
                new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new Ansi(false));
    }

    @Test
    void askReturnsTypedAnswer() {
        assertThat(promptFeeding("scan.pdf\n").ask("Input", null)).isEqualTo("scan.pdf");
    }

    @Test
    void askEmptyTakesDefault() {
        assertThat(promptFeeding("\n").ask("Output", "book.pdf")).isEqualTo("book.pdf");
    }

    @Test
    void askRequiredRepromptsUntilNonEmpty() {
        assertThat(promptFeeding("\n\nfinally\n").ask("Input", null)).isEqualTo("finally");
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("a value is required");
    }

    @Test
    void confirmParsesYesNoAndDefault() {
        assertThat(promptFeeding("y\n").confirm("ok?", false)).isTrue();
        assertThat(promptFeeding("no\n").confirm("ok?", true)).isFalse();
        assertThat(promptFeeding("\n").confirm("ok?", true)).isTrue();
    }

    @Test
    void confirmRepromptsOnGarbage() {
        assertThat(promptFeeding("maybe\ny\n").confirm("ok?", false)).isTrue();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("please answer y or n");
    }

    @Test
    void selectReturnsChosenValueByNumber() {
        List<Prompt.Choice<String>> choices =
                List.of(Prompt.Choice.of("RTL", "rtl"), Prompt.Choice.of("LTR", "ltr"));
        assertThat(promptFeeding("2\n").select("dir", choices, choices.get(0))).isEqualTo("ltr");
    }

    @Test
    void selectEmptyTakesDefault() {
        List<Prompt.Choice<String>> choices =
                List.of(Prompt.Choice.of("RTL", "rtl"), Prompt.Choice.of("LTR", "ltr"));
        assertThat(promptFeeding("\n").select("dir", choices, choices.get(0))).isEqualTo("rtl");
    }

    @Test
    void selectRepromptsOnOutOfRange() {
        List<Prompt.Choice<String>> choices =
                List.of(Prompt.Choice.of("RTL", "rtl"), Prompt.Choice.of("LTR", "ltr"));
        assertThat(promptFeeding("9\n1\n").select("dir", choices, choices.get(1))).isEqualTo("rtl");
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("enter a number from 1 to 2");
    }

    @Test
    void closedInputFailsFast() {
        assertThatThrownBy(() -> promptFeeding("").ask("Input", null))
                .isInstanceOf(UncheckedIOException.class);
    }
}
