package io.github.p4suta.pipeline.cli;

import io.github.p4suta.shared.progress.JsonlProgressCodec;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ProgressSink} that appends each event as one JSONL line ({@link JsonlProgressCodec}) to
 * a file, flushing per line so a reader tailing the file (the web layer's {@code WatchService})
 * sees events as they happen. Thread-safe: emits may arrive concurrently from the parallel page
 * workers.
 *
 * <p>Best-effort: a failed write is logged and swallowed, never propagated — progress reporting
 * must not break (or mask the cause of) a conversion. The file is created (truncating any previous
 * run's events) when the sink is opened and closed by {@link #close()}.
 */
public final class JsonlFileProgressSink implements ProgressSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JsonlFileProgressSink.class);

    private final Path file;
    private final BufferedWriter writer;

    /**
     * Opens {@code file} for writing, truncating any existing contents.
     *
     * @param file the JSONL progress file to write
     * @throws IOException if the file cannot be opened
     */
    public JsonlFileProgressSink(Path file) throws IOException {
        this.file = file;
        this.writer =
                Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public synchronized void emit(ProgressEvent event) {
        try {
            writer.write(JsonlProgressCodec.write(event));
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            log.warn("could not write progress event to {}: {}", file, e.getMessage());
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
