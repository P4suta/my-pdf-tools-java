package io.github.p4suta.pipeline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.progress.JsonlProgressCodec;
import io.github.p4suta.shared.progress.ProgressEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlFileProgressSinkTest {

    @TempDir Path tmp;

    @Test
    void writesEachEventAsOneJsonlLineThatDecodesBack() throws IOException {
        Path file = tmp.resolve("events.jsonl");
        List<ProgressEvent> events =
                List.of(
                        new ProgressEvent.RunStarted(3),
                        new ProgressEvent.StageStarted("extract", 0, 3),
                        new ProgressEvent.RunCompleted());

        try (JsonlFileProgressSink sink = new JsonlFileProgressSink(file)) {
            events.forEach(sink::emit);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(3);
        assertThat(lines.stream().map(JsonlProgressCodec::read).toList())
                .containsExactlyElementsOf(events);
    }

    @Test
    void truncatesAnyPreviousContentsOnOpen() throws IOException {
        Path file = tmp.resolve("events.jsonl");
        Files.writeString(file, "stale line\nanother\n");

        try (JsonlFileProgressSink sink = new JsonlFileProgressSink(file)) {
            sink.emit(new ProgressEvent.RunCompleted());
        }

        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8))
                .containsExactly("{\"type\":\"runCompleted\"}");
    }
}
