package io.github.p4suta.shared.pdf;

import io.github.p4suta.shared.process.ProcessRunner;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * Wraps the {@code qpdf} binary's {@code --linearize} pass. Writes a linearized (Fast-Web-View)
 * copy of an input PDF to an output path, optionally bumping the {@code %PDF-x.y} header byte
 * ({@code --min-version}) and guaranteeing an EOL before every {@code endstream} ({@code
 * --newline-before-endstream}, which PDF/A mandates after linearisation repacks streams).
 *
 * <p>Returns the {@link ProcessRunner.Result} (qpdf's exit {@code 3}, "succeeded with warnings", is
 * accepted) and propagates {@link IOException} / {@link TimeoutException} / {@link
 * InterruptedException} unchanged. The failure policy is decided by the caller: each app wraps
 * {@link #linearize} with its own.
 *
 * <p>The {@code qpdf} binary is resolved through {@link ToolPath}: an explicit {@code
 * -D<qpdfPropertyKey>} override wins, else the first {@code qpdf} on {@code PATH}. The property key
 * is a constructor parameter (e.g. {@code despeckle.qpdf.path}). {@link #isAvailable()} probes
 * resolution up front; {@link #linearize} surfaces an unresolved binary as a plain {@link
 * IOException} so the missing-tool policy stays with the app.
 */
public final class QpdfRunner {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    // qpdf exits 3 on "succeeded with warnings"; the linearized output is still valid, so 3 is
    // accepted. Any other non-zero exit surfaces as an IOException from the runner.
    private static final Set<Integer> ACCEPTABLE_EXITS = Set.of(3);

    private final String qpdfPropertyKey;
    private final Duration timeout;
    private final @Nullable String minVersion;
    private final boolean newlineBeforeEndstream;

    private QpdfRunner(
            String qpdfPropertyKey,
            Duration timeout,
            @Nullable String minVersion,
            boolean newlineBeforeEndstream) {
        this.qpdfPropertyKey = qpdfPropertyKey;
        this.timeout = timeout;
        this.minVersion = minVersion;
        this.newlineBeforeEndstream = newlineBeforeEndstream;
    }

    /**
     * A plain {@code --linearize} runner: no {@code --min-version}, no {@code
     * --newline-before-endstream}, the default two-minute timeout.
     *
     * @param qpdfPropertyKey the {@code -D} override the app uses to point at its bundled {@code
     *     qpdf} (e.g. {@code despeckle.qpdf.path})
     */
    public static QpdfRunner linearizeOnly(String qpdfPropertyKey) {
        return new QpdfRunner(qpdfPropertyKey, DEFAULT_TIMEOUT, null, false);
    }

    /**
     * A runner that also rewrites the header to {@code minVersion} and (when {@code
     * newlineBeforeEndstream}) adds the PDF/A EOL marker.
     *
     * @param qpdfPropertyKey the {@code -D} override the app uses to point at its bundled {@code
     *     qpdf}
     * @param minVersion the {@code x.y} version to pass to {@code --min-version} (e.g. {@code 1.7})
     * @param newlineBeforeEndstream whether to pass {@code --newline-before-endstream}
     */
    public static QpdfRunner modernizing(
            String qpdfPropertyKey, String minVersion, boolean newlineBeforeEndstream) {
        return new QpdfRunner(qpdfPropertyKey, DEFAULT_TIMEOUT, minVersion, newlineBeforeEndstream);
    }

    /** A copy of this runner with a different timeout. */
    public QpdfRunner withTimeout(Duration timeout) {
        return new QpdfRunner(qpdfPropertyKey, timeout, minVersion, newlineBeforeEndstream);
    }

    /**
     * Whether a {@code qpdf} binary resolves for this runner's property key / {@code PATH}. Lets an
     * app pick its "missing tool" policy (skip the pass, warn, or fall back) before calling {@link
     * #linearize}.
     */
    public boolean isAvailable() {
        return ToolPath.resolve("qpdf", qpdfPropertyKey).isPresent();
    }

    /**
     * Write a linearized copy of {@code input} to {@code output} via {@code qpdf}. Exit {@code 0}
     * and the accepted warning exit {@code 3} return the runner {@link ProcessRunner.Result}; any
     * other exit, a launch failure, or an unresolved binary throws an {@link IOException}.
     *
     * @param input the source PDF
     * @param output the linearized PDF to write (any sibling temp + move is the caller's job, to
     *     avoid qpdf's {@code --replace-input} backup litter)
     * @return the finished process result (exit code, captured stdout/stderr, elapsed time)
     * @throws IOException if the binary is unresolved, cannot be started, or exits unacceptably
     * @throws TimeoutException if {@code qpdf} does not finish within the timeout (it is killed)
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public ProcessRunner.Result linearize(Path input, Path output)
            throws IOException, InterruptedException, TimeoutException {
        String qpdf =
                ToolPath.resolve("qpdf", qpdfPropertyKey)
                        .map(Path::toString)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "qpdf not found on PATH; install it or set -D"
                                                        + qpdfPropertyKey
                                                        + "=/path/to/qpdf"));
        List<String> command = new ArrayList<>();
        command.add(qpdf);
        command.add("--linearize");
        if (newlineBeforeEndstream) {
            command.add("--newline-before-endstream");
        }
        if (minVersion != null) {
            command.add("--min-version=" + minVersion);
        }
        command.add(input.toString());
        command.add(output.toString());
        return ProcessRunner.run(List.copyOf(command), timeout, ACCEPTABLE_EXITS);
    }

    /** The resolved {@code qpdf} path, if any — for an app that wants to log it. */
    public Optional<Path> binaryPath() {
        return ToolPath.resolve("qpdf", qpdfPropertyKey);
    }
}
