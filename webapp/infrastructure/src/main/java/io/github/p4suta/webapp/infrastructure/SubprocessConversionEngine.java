package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.shared.progress.JsonlProgressCodec;
import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.port.ConversionEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs pdfbook as a child process for isolation, so a native crash inside Leptonica or a
 * shelled-out tool cannot take the server JVM down. The child writes its result to {@code -o} and a
 * stream of JSONL progress events to a private {@code --progress-file}; this engine tails that file
 * by byte offset while the child runs and drains it once more after exit, decoding each complete
 * line into a {@link io.github.p4suta.shared.progress.ProgressEvent} for the supplied sink.
 *
 * <p>The tail tracks an offset and decodes only whole lines (split on the {@code \n} byte, which
 * never falls inside a UTF-8 sequence), so a half-written line or a multi-byte character straddling
 * two polls is decoded correctly rather than corrupted. The OS-level {@code WatchService} is
 * avoided: append notifications coalesce and the final lines often land after the last wake, so
 * polling plus a final drain is needed.
 */
public final class SubprocessConversionEngine implements ConversionEngine {

    private static final Logger log = LoggerFactory.getLogger(SubprocessConversionEngine.class);
    private static final Duration POLL = Duration.ofMillis(150);

    private final Path pdfbookBinary;
    private final Duration timeout;

    /**
     * @param pdfbookBinary the pdfbook executable to run
     * @param timeout how long a single conversion may run before it is killed
     */
    public SubprocessConversionEngine(Path pdfbookBinary, Duration timeout) {
        this.pdfbookBinary = pdfbookBinary;
        this.timeout = timeout;
    }

    @Override
    public void convert(
            ConversionRequest request, Path inputPdf, Path outputPdf, ProgressSink progress)
            throws IOException {
        Path progressFile = Files.createTempFile("pdfbook-progress-", ".jsonl");
        try {
            List<String> command = buildCommand(request, inputPdf, outputPdf, progressFile);
            log.info("running pdfbook: {}", command);
            ProcessBuilder builder =
                    new ProcessBuilder(command)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD);
            // jpackage-nesting gotcha: when this server runs as the self-contained app-image
            // and spawns the NESTED pdfbook app-image, the outer jpackage launcher has
            // setenv'd its re-launch marker _JPACKAGE_LAUNCHER plus the platform dynamic-linker
            // path (LD_LIBRARY_PATH on Linux, DYLD_LIBRARY_PATH on macOS) into our environment.
            // pdfbook is ALSO a jpackage launcher; inheriting that PAIR makes it think it is mid
            // re-launch and parse the first app arg as a JVM option (it dies with "Unrecognized
            // option: -o" / "Could not create the Java Virtual Machine"), failing every
            // conversion. Strip them so the nested launcher starts clean. A no-op off the
            // app-image (the Docker runtime / `java -jar`), where none of these vars is set.
            builder.environment().remove("_JPACKAGE_LAUNCHER");
            builder.environment().remove("LD_LIBRARY_PATH");
            builder.environment().remove("DYLD_LIBRARY_PATH");
            Process process = builder.start();
            runToCompletion(process, progressFile, progress);
        } finally {
            Files.deleteIfExists(progressFile);
        }
    }

    private void runToCompletion(Process process, Path progressFile, ProgressSink progress)
            throws IOException {
        ProgressFileTail tail = new ProgressFileTail(progressFile);
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!process.waitFor(POLL.toMillis(), TimeUnit.MILLISECONDS)) {
                tail.drainTo(progress);
                if (System.nanoTime() >= deadline) {
                    process.destroyForcibly();
                    throw new IOException("pdfbook timed out after " + timeout);
                }
            }
            // Final drain: the last lines can land after the last poll, so read once more to EOF
            // now
            // that the process has exited.
            tail.drainTo(progress);
            int exit = process.exitValue();
            if (exit != 0) {
                throw new IOException("pdfbook exited with code " + exit);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running pdfbook", e);
        }
    }

    private List<String> buildCommand(
            ConversionRequest request, Path inputPdf, Path outputPdf, Path progressFile) {
        List<String> command = new ArrayList<>();
        command.add(pdfbookBinary.toString());
        command.add("-o");
        command.add(outputPdf.toString());
        command.add("-d");
        command.add(request.direction() == Direction.RTL ? "RTL" : "LTR");
        command.add("--first-page");
        command.add(
                switch (request.firstPage()) {
                    case RIGHT -> "right";
                    case LEFT -> "left";
                    case COVER -> "cover";
                });
        if (!request.despeckle()) {
            command.add("--no-despeckle");
        }
        if (!request.register()) {
            command.add("--no-register");
        }
        if (!request.deskew()) {
            command.add("--no-deskew");
        }
        if (!request.scale()) {
            command.add("--no-scale");
        }
        if (request.pdfA()) {
            command.add("--pdf-a");
        }
        command.add("-j");
        command.add(Integer.toString(request.jobs()));
        command.add("--progress-file");
        command.add(progressFile.toString());
        command.add(inputPdf.toString());
        return command;
    }

    /**
     * Incremental, offset-tracking tail of the JSONL progress file that decodes only whole lines.
     */
    private static final class ProgressFileTail {

        private final Path file;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private long offset;

        ProgressFileTail(Path file) {
            this.file = file;
        }

        void drainTo(ProgressSink sink) throws IOException {
            if (!Files.exists(file)) {
                return;
            }
            long size = Files.size(file);
            if (size <= offset) {
                return;
            }
            byte[] chunk = new byte[(int) (size - offset)];
            try (SeekableByteChannel channel =
                    Files.newByteChannel(file, StandardOpenOption.READ)) {
                channel.position(offset);
                ByteBuffer buffer = ByteBuffer.wrap(chunk);
                int total = 0;
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                }
                offset += total;
                pending.write(chunk, 0, total);
            }
            emitCompleteLines(sink);
        }

        private void emitCompleteLines(ProgressSink sink) {
            byte[] buffer = pending.toByteArray();
            int lineStart = 0;
            int consumed = 0;
            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i] == (byte) '\n') {
                    String line =
                            new String(buffer, lineStart, i - lineStart, StandardCharsets.UTF_8)
                                    .strip();
                    lineStart = i + 1;
                    consumed = lineStart;
                    emit(sink, line);
                }
            }
            pending.reset();
            pending.write(buffer, consumed, buffer.length - consumed);
        }

        private static void emit(ProgressSink sink, String line) {
            if (line.isEmpty()) {
                return;
            }
            try {
                sink.emit(JsonlProgressCodec.read(line));
            } catch (RuntimeException e) {
                log.warn("skipping unparsable progress line: {}", line);
            }
        }
    }
}
