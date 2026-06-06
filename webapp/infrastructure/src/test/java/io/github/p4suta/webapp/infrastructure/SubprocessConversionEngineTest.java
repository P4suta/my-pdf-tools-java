package io.github.p4suta.webapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real subprocess + progress-file tail against fake pdfbook scripts (the dev image has
 * bash), covering success with mid-run draining, failure, timeout, unparsable lines, and the full
 * option-to-flag mapping.
 */
class SubprocessConversionEngineTest {

    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    // Writes runStarted + stageStarted, sleeps past one poll, then stageCompleted + runCompleted
    // and
    // the result — so both the in-loop drain and the post-exit final drain are exercised.
    private static final String SLOW_SUCCESS =
            """
            #!/usr/bin/env bash
            out=""
            progress=""
            while [ "$#" -gt 0 ]; do
              case "$1" in
                -o) out="$2"; shift 2 ;;
                --progress-file) progress="$2"; shift 2 ;;
                *) shift ;;
              esac
            done
            echo '{"type":"runStarted","stageCount":2}' >> "$progress"
            echo '{"type":"stageStarted","stage":"extract","index":0,"stageCount":2}' >> "$progress"
            sleep 0.3
            echo '{"type":"stageCompleted","stage":"extract"}' >> "$progress"
            echo '{"type":"runCompleted"}' >> "$progress"
            printf 'PDF' > "$out"
            """;

    private static final String FAST_SUCCESS =
            """
            #!/usr/bin/env bash
            out=""
            progress=""
            while [ "$#" -gt 0 ]; do
              case "$1" in
                -o) out="$2"; shift 2 ;;
                --progress-file) progress="$2"; shift 2 ;;
                *) shift ;;
              esac
            done
            echo '{"type":"runCompleted"}' >> "$progress"
            printf 'PDF' > "$out"
            """;

    private static final String FAILURE =
            """
            #!/usr/bin/env bash
            progress=""
            while [ "$#" -gt 0 ]; do
              case "$1" in
                --progress-file) progress="$2"; shift 2 ;;
                *) shift ;;
              esac
            done
            echo '{"type":"runStarted","stageCount":2}' >> "$progress"
            echo '{"type":"runFailed","kind":"EXTRACT","message":"pdfimages not found"}' >> "$progress"
            exit 1
            """;

    private static final String TIMEOUT =
            """
            #!/usr/bin/env bash
            sleep 10
            """;

    private static final String GARBAGE =
            """
            #!/usr/bin/env bash
            progress=""
            while [ "$#" -gt 0 ]; do
              case "$1" in
                --progress-file) progress="$2"; shift 2 ;;
                *) shift ;;
              esac
            done
            echo 'not json at all' >> "$progress"
            echo '{"type":"runCompleted"}' >> "$progress"
            """;

    @TempDir Path tmp;
    private final List<ProgressEvent> events = new ArrayList<>();
    private int counter;

    private Path script(String content) throws IOException {
        Path script = tmp.resolve("pdfbook-" + counter++);
        Files.writeString(script, content);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private Path input() throws IOException {
        Path input = tmp.resolve("input.pdf");
        Files.writeString(input, "scan");
        return input;
    }

    private SubprocessConversionEngine engine(String scriptBody, Duration timeout)
            throws IOException {
        return new SubprocessConversionEngine(script(scriptBody), timeout);
    }

    @Test
    void streamsEventsAndWritesTheResultOnSuccess() throws IOException {
        Path output = tmp.resolve("out.pdf");

        engine(SLOW_SUCCESS, Duration.ofSeconds(10)).convert(REQUEST, input(), output, events::add);

        assertThat(events)
                .containsExactly(
                        new ProgressEvent.RunStarted(2),
                        new ProgressEvent.StageStarted("extract", 0, 2),
                        new ProgressEvent.StageCompleted("extract"),
                        new ProgressEvent.RunCompleted());
        assertThat(Files.readString(output)).isEqualTo("PDF");
    }

    @Test
    void emitsTheFailureEventAndThrowsOnANonZeroExit() throws IOException {
        SubprocessConversionEngine engine = engine(FAILURE, Duration.ofSeconds(10));

        assertThatThrownBy(
                        () -> engine.convert(REQUEST, input(), tmp.resolve("out.pdf"), events::add))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exited with code 1");
        assertThat(events).contains(new ProgressEvent.RunFailed("EXTRACT", "pdfimages not found"));
    }

    @Test
    void killsTheProcessAndThrowsOnTimeout() throws IOException {
        SubprocessConversionEngine engine = engine(TIMEOUT, Duration.ofMillis(300));

        assertThatThrownBy(
                        () -> engine.convert(REQUEST, input(), tmp.resolve("out.pdf"), events::add))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void skipsUnparsableProgressLines() throws IOException {
        engine(GARBAGE, Duration.ofSeconds(10))
                .convert(REQUEST, input(), tmp.resolve("out.pdf"), events::add);

        assertThat(events).containsExactly(new ProgressEvent.RunCompleted());
    }

    @Test
    void mapsEveryOptionToItsFlag() throws IOException {
        SubprocessConversionEngine engine = engine(FAST_SUCCESS, Duration.ofSeconds(10));
        for (FirstPage firstPage : FirstPage.values()) {
            ConversionRequest toggled =
                    new ConversionRequest(
                            Direction.LTR, firstPage, false, false, false, false, true, 1);
            List<ProgressEvent> got = new ArrayList<>();
            engine.convert(toggled, input(), tmp.resolve("o-" + firstPage + ".pdf"), got::add);
            assertThat(got).containsExactly(new ProgressEvent.RunCompleted());
        }
    }
}
