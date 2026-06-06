package io.github.p4suta.shared.cli;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Refuses to overwrite an existing output unless {@code --force} was given.
 *
 * <p>Single-file CLIs ({@code tate-yoko-pdf}, {@code pdfbook}) call {@link #refuseIfExists} on the
 * user's {@code -o} path before producing output, so a run never clobbers an existing file
 * silently. This matches the {@code register}/{@code despeckle} pipeline convention (refuse with
 * exit code 73, {@code EX_CANTCREAT}); batch modes skip-existing-unless-force instead, which
 * callers handle directly. The guard sits on the user's destination only — a {@code -} (stdout) run
 * bridges through a fresh temp file with no final path to protect, so callers skip the check there.
 */
public final class OutputGuard {

    private OutputGuard() {}

    /**
     * Throws {@link OutputExistsException} when {@code output} already exists and {@code force} is
     * {@code false}; otherwise returns normally.
     *
     * @param output the user's destination path
     * @param force whether {@code --force} was given (allowing an overwrite)
     */
    public static void refuseIfExists(Path output, boolean force) {
        if (!force && Files.exists(output)) {
            throw new OutputExistsException(output);
        }
    }

    /**
     * Raised when an output path already exists and {@code --force} was not given. Carries {@link
     * CommonErrorKind#OUTPUT_CONFLICT} (so the shared mapper yields exit code 73) and the
     * conflicting path as the technical detail; the user-facing English text comes from the CLI's
     * {@code CliErrorMessages} catalog like every other kind.
     */
    public static final class OutputExistsException extends BaseAppException {

        private static final long serialVersionUID = 1L;

        OutputExistsException(Path output) {
            super(CommonErrorKind.OUTPUT_CONFLICT, output.toString(), (@Nullable Throwable) null);
        }
    }
}
