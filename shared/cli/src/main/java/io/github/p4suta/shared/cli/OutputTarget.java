package io.github.p4suta.shared.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a single processing run should write: a concrete file, or stdout.
 *
 * <p>A writer that needs a real, seekable path (a PDF assembler, an image encoder) cannot write to
 * stdout directly. {@link #write} bridges that: it hands the supplied action a real path — the
 * destination file, or a fresh temp file for stdout — then, for stdout, streams the result to
 * {@code System.out} and removes the temp — always, even on failure or an interrupting signal (the
 * temp is created via {@link TempFiles}).
 *
 * @param toStdout whether the result is to be streamed to {@code System.out}
 * @param file the destination file when {@link #toStdout()} is {@code false}, otherwise {@code
 *     null}
 * @param tempPrefix the temp-file name prefix used for the stdout bridge
 * @param tempSuffix the temp-file name suffix (including any dot) used for the stdout bridge
 */
public record OutputTarget(
        boolean toStdout, @Nullable Path file, String tempPrefix, String tempSuffix) {

    /**
     * A stdout target whose bridge temp file is named {@code <tempPrefix>…<tempSuffix>}.
     *
     * @param tempPrefix the temp-file name prefix (app-chosen, e.g. {@code "tate-yoko-out"})
     * @param tempSuffix the temp-file name suffix including any dot (app-chosen, e.g. {@code
     *     ".pdf"})
     * @return the stdout target
     */
    public static OutputTarget stdout(String tempPrefix, String tempSuffix) {
        return new OutputTarget(true, null, tempPrefix, tempSuffix);
    }

    /**
     * A file target writing straight to {@code path}.
     *
     * @param path the destination file
     * @return the file target
     */
    public static OutputTarget file(Path path) {
        return new OutputTarget(false, path, "", "");
    }

    /**
     * Runs {@code action} against the concrete path to write to, then completes the write: for
     * stdout, streams the produced bytes to {@code System.out} and deletes the temp file; for a
     * file target the path is the destination and there is nothing else to do.
     *
     * @param action the work that produces the output at the handed path
     * @throws IOException if the action, or the stream-out, fails
     */
    public void write(IoPathAction action) throws IOException {
        write(action, System.out);
    }

    /**
     * Package-private seam: same as {@link #write(IoPathAction)} but streaming the stdout result to
     * {@code sink}, so a test can capture it without mutating {@code System.out}.
     */
    void write(IoPathAction action, OutputStream sink) throws IOException {
        Path realOut = toStdout ? TempFiles.createTempFile(tempPrefix, tempSuffix) : requireFile();
        try {
            action.accept(realOut);
            if (toStdout) {
                // Files.copy(Path, OutputStream) does not close the sink (e.g. System.out).
                Files.copy(realOut, sink);
                sink.flush();
            }
        } finally {
            if (toStdout) {
                TempFiles.delete(realOut);
            }
        }
    }

    private Path requireFile() {
        return Objects.requireNonNull(file, "file target must have a path");
    }
}
