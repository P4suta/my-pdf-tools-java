package io.github.p4suta.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class ConsoleProgressListenerTest {

    @Test
    void onStartPrintsTotal() {
        Probe probe = new Probe(true);
        probe.listener.onStart(7);
        assertThat(probe.text()).contains("Processing 7 spreads");
    }

    @Test
    void interactiveSpreadsUseCarriageReturnForOverwrite() {
        Probe probe = new Probe(true);
        probe.listener.onSpreadComplete(1, 4);
        probe.listener.onSpreadComplete(2, 4);
        assertThat(probe.text()).startsWith("\r").contains("[1/4]").contains("[2/4]");
    }

    @Test
    void redirectedSpreadsUseNewlinesNotCarriageReturns() {
        Probe probe = new Probe(false);
        probe.listener.onSpreadComplete(1, 4);
        probe.listener.onSpreadComplete(2, 4);
        String text = probe.text();
        assertThat(text).doesNotContain("\r");
        assertThat(text.lines().toList())
                .containsExactly("[1/4] spreads completed", "[2/4] spreads completed");
    }

    @Test
    void onCompletePrintsDurationWithOneDecimal() {
        Probe probe = new Probe(true);
        probe.listener.onComplete(1500);
        assertThat(probe.text()).contains("Done in 1.5 seconds");
    }

    @Test
    void labelIsPrefixedToEachLine() {
        Probe probe = new Probe(true, "[2/5] novel.pdf");
        probe.listener.onStart(3);
        probe.listener.onSpreadComplete(1, 3);
        assertThat(probe.text()).contains("[2/5] novel.pdf Processing 3 spreads");
        assertThat(probe.text()).contains("\r[2/5] novel.pdf [1/3]");
    }

    private static final class Probe {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ConsoleProgressListener listener;

        Probe(boolean interactive) {
            this(interactive, null);
        }

        Probe(boolean interactive, @Nullable String label) {
            listener =
                    new ConsoleProgressListener(
                            new PrintStream(buffer, true, StandardCharsets.UTF_8),
                            label,
                            interactive);
        }

        String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
