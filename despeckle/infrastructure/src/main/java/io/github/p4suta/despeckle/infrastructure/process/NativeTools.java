package io.github.p4suta.despeckle.infrastructure.process;

import io.github.p4suta.despeckle.domain.exception.DespeckleErrorKind;
import io.github.p4suta.despeckle.domain.exception.DespeckleException;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves and runs the external native tools the PDF pipeline shells out to ({@code pdfimages},
 * {@code pdfinfo}, {@code jbig2}, {@code qpdf}). Discovery is delegated to the cross-app {@link
 * ToolPath} island: an explicit {@code -Ddespeckle.<tool>.path} wins (this is how the packaged
 * app-image points at its bundled binaries), else the tool is looked up on {@code PATH}. A missing
 * extraction/encode tool is fatal — the pipeline cannot proceed — so {@link #resolve} maps {@code
 * ToolPath}'s empty result to a thrown {@link DespeckleException}; only the final {@code qpdf}
 * linearize degrades gracefully (its failure is caught by the caller).
 *
 * <p>The launch helpers stay local rather than routing through {@code :shared:process}'s {@code
 * ProcessRunner}/{@code Tasks}: {@link #capture} returns the raw {@code byte[]} a JBIG2 stream
 * needs (the shared runner decodes to a UTF-8 {@code String}, which is lossy for binary output),
 * and {@link #run} raises/propagates the tagged {@link DespeckleException} kind (the shared {@code
 * Tasks} flattens any non-{@code IOException} cause, losing the kind and its exit code). The {@code
 * qpdf} call site, whose output is text and whose exit-3 tolerance the shared runner models
 * directly, uses {@code ProcessRunner} instead.
 *
 * <p>Public so the sibling {@code infrastructure.pdf} adapters can call it; it never leaves the
 * {@code :infrastructure} module (the application and CLI layers depend only on the {@code :port}
 * abstractions), so this is module-internal surface, not part of the tool's API.
 */
public final class NativeTools {

    private NativeTools() {}

    public static String pdfimages() throws IOException {
        return resolve("pdfimages", "despeckle.pdfimages.path");
    }

    public static String pdfinfo() throws IOException {
        return resolve("pdfinfo", "despeckle.pdfinfo.path");
    }

    public static String jbig2() throws IOException {
        return resolve("jbig2", "despeckle.jbig2.path");
    }

    public static String qpdf() throws IOException {
        return resolve("qpdf", "despeckle.qpdf.path");
    }

    /**
     * The tool path via {@link ToolPath#resolve}: {@code -D<property>} if set, else the first
     * executable named {@code tool} on PATH. A missing tool is fatal here, so the empty result is
     * mapped to a thrown {@link DespeckleException}.
     */
    static String resolve(String tool, String property) {
        return ToolPath.resolve(tool, property)
                .map(Path::toString)
                .orElseThrow(
                        () ->
                                DespeckleException.withDetail(
                                        DespeckleErrorKind.NATIVE_TOOL_FAILED,
                                        tool
                                                + " not found on PATH; install it or set -D"
                                                + property
                                                + "=/path/to/"
                                                + tool,
                                        null));
    }

    /** Run a command, discarding all output, failing on nonzero exit or timeout. */
    public static void run(List<String> command, long timeoutSeconds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        awaitExit(pb.start(), command, timeoutSeconds);
    }

    /** Run a command and return its stdout bytes, failing on nonzero exit or timeout. */
    public static byte[] capture(List<String> command, long timeoutSeconds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        byte[] out;
        try (InputStream in = process.getInputStream()) {
            // The output we capture (a -list/-version report, or one page's JBIG2 stream) is small
            // and stderr is discarded, so reading stdout to EOF before waiting cannot deadlock.
            out = in.readAllBytes();
        }
        awaitExit(process, command, timeoutSeconds);
        return out;
    }

    private static void awaitExit(Process process, List<String> command, long timeoutSeconds)
            throws IOException {
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw DespeckleException.withDetail(
                        DespeckleErrorKind.NATIVE_TOOL_FAILED,
                        command.get(0) + " timed out after " + timeoutSeconds + "s",
                        null);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(command.get(0) + " was interrupted", e);
        }
        int code = process.exitValue();
        if (code != 0) {
            throw DespeckleException.withDetail(
                    DespeckleErrorKind.NATIVE_TOOL_FAILED,
                    command.get(0) + " failed with exit code " + code,
                    null);
        }
    }
}
