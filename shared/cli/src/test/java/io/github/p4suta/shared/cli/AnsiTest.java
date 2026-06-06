package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ANSI styling wraps text only when enabled, and passes it through verbatim when not. */
final class AnsiTest {

    @Test
    void disabledIsPlainPassthrough() {
        Ansi ansi = new Ansi(false);
        assertThat(ansi.enabled()).isFalse();
        assertThat(ansi.red("x")).isEqualTo("x");
        assertThat(ansi.bold("x")).isEqualTo("x");
    }

    @Test
    void enabledWrapsWithEscapeAndReset() {
        Ansi ansi = new Ansi(true);
        String esc = String.valueOf((char) 27);
        assertThat(ansi.green("ok")).isEqualTo(esc + "[32mok" + esc + "[0m");
        assertThat(ansi.bold("b")).startsWith(esc + "[1m").endsWith(esc + "[0m").contains("b");
    }
}
