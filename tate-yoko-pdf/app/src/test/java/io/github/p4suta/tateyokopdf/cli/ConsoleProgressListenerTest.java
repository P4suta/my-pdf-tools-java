package io.github.p4suta.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(value = Resources.SYSTEM_ERR, mode = ResourceAccessMode.READ_WRITE)
final class ConsoleProgressListenerTest {

    @Test
    void onStartPrintsTotal() {
        String out = captureStderr(() -> new ConsoleProgressListener().onStart(7));
        assertThat(out).contains("Processing 7 spreads");
    }

    @Test
    void onSpreadCompleteUsesCarriageReturnForOverwrite() {
        String out =
                captureStderr(
                        () -> {
                            var l = new ConsoleProgressListener();
                            l.onSpreadComplete(1, 4);
                            l.onSpreadComplete(2, 4);
                        });
        assertThat(out).startsWith("\r").contains("[1/4]").contains("[2/4]");
    }

    @Test
    void onCompletePrintsDurationWithOneDecimal() {
        String out = captureStderr(() -> new ConsoleProgressListener().onComplete(1500));
        assertThat(out).contains("Done in 1.5 seconds");
    }

    @Test
    void labelIsPrefixedToEachLine() {
        String out =
                captureStderr(
                        () -> {
                            var l = new ConsoleProgressListener("[2/5] novel.pdf");
                            l.onStart(3);
                            l.onSpreadComplete(1, 3);
                        });
        assertThat(out).contains("[2/5] novel.pdf Processing 3 spreads");
        assertThat(out).contains("\r[2/5] novel.pdf [1/3]");
    }

    private static String captureStderr(Runnable body) {
        PrintStream original = System.err;
        var buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
