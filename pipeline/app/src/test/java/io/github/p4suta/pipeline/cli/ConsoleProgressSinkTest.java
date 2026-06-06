package io.github.p4suta.pipeline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.cli.Ansi;
import io.github.p4suta.shared.progress.ProgressEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The interactive progress bar renders stage lines, an in-place page bar, and terminal markers. */
final class ConsoleProgressSinkTest {

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    private ConsoleProgressSink sink(boolean interactive) {
        return new ConsoleProgressSink(
                new PrintStream(buf, true, StandardCharsets.UTF_8), new Ansi(false), interactive);
    }

    private String text() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void rendersStageLinesAndDoneMarker() {
        ConsoleProgressSink s = sink(true);
        s.emit(new ProgressEvent.RunStarted(2));
        s.emit(new ProgressEvent.StageStarted("despeckle", 0, 2));
        s.emit(new ProgressEvent.PageProcessed("despeckle", 1, 4));
        s.emit(new ProgressEvent.StageCompleted("despeckle"));
        s.emit(new ProgressEvent.RunCompleted());
        String t = text();
        assertThat(t).contains("[1/2] despeckle").contains("1/4").contains("done");
    }

    @Test
    void nonInteractiveSuppressesPerPageBar() {
        ConsoleProgressSink s = sink(false);
        s.emit(new ProgressEvent.StageStarted("register", 1, 2));
        s.emit(new ProgressEvent.PageProcessed("register", 2, 5));
        assertThat(text()).contains("[2/2] register").doesNotContain("2/5").doesNotContain("\r");
    }

    @Test
    void runFailedShowsKind() {
        ConsoleProgressSink s = sink(true);
        s.emit(new ProgressEvent.RunFailed("OUTPUT_CONFLICT", "x"));
        assertThat(text()).contains("OUTPUT_CONFLICT");
    }
}
