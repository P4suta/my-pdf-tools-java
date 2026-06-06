package io.github.p4suta.shared.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Bridges a single input arriving on {@code System.in} to the file-based processing path. */
public final class StdinSource {

    private StdinSource() {}

    /**
     * Drains {@code System.in} into a fresh temp file named {@code <prefix>…<suffix>}, runs {@code
     * action} against it, and deletes the temp afterwards — always, even on failure or an
     * interrupting signal (the temp is created via {@link TempFiles}). {@code
     * Files.copy(InputStream, …)} does not close {@code System.in}.
     *
     * @param prefix the temp-file name prefix (app-chosen, e.g. {@code "tate-yoko-in"})
     * @param suffix the temp-file name suffix including any dot (app-chosen, e.g. {@code ".pdf"})
     * @param action the work to run against the drained temp file
     * @throws IOException if draining stdin, or the action itself, fails
     */
    public static void withStdin(String prefix, String suffix, IoPathAction action)
            throws IOException {
        withStdin(System.in, prefix, suffix, action);
    }

    /**
     * Package-private seam: same as {@link #withStdin(String, String, IoPathAction)} but draining
     * the supplied stream, so a test can feed a deterministic input without mutating {@code
     * System.in}.
     */
    static void withStdin(InputStream in, String prefix, String suffix, IoPathAction action)
            throws IOException {
        Path tmpIn = TempFiles.createTempFile(prefix, suffix);
        try {
            Files.copy(in, tmpIn, StandardCopyOption.REPLACE_EXISTING);
            action.accept(tmpIn);
        } finally {
            TempFiles.delete(tmpIn);
        }
    }
}
