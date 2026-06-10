package io.github.p4suta.register.infrastructure.process;

import io.github.p4suta.register.domain.exception.RegisterErrorKind;
import io.github.p4suta.register.domain.exception.RegisterException;
import io.github.p4suta.shared.process.ProcessRunner;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Resolves and runs the external native tools the PDF pipeline shells out to ({@code pdfimages},
 * {@code pdfinfo}, {@code jbig2}). This is the thin app-side adapter onto the cross-app {@code
 * io.github.p4suta.shared.process} plumbing: discovery is delegated to {@link
 * io.github.p4suta.shared.process.ToolPath} (passing register's per-tool {@code
 * -Dregister.<tool>.path} override key — this is how the packaged app-image points at its bundled
 * binaries, else the tool is looked up on {@code PATH}), and the discard-output run is delegated to
 * {@link io.github.p4suta.shared.process.ProcessRunner}. Unlike the optional flip-book, a missing
 * pipeline tool is fatal — the pipeline cannot proceed — so resolution throws, and the shared
 * layer's neutral {@code IOException}/{@code TimeoutException} failures are re-mapped to register's
 * {@code RegisterException} (NATIVE_TOOL_FAILED) so the pipeline keeps its own error wording.
 */
public final class NativeTools {

    private NativeTools() {}

    public static String pdfimages() throws IOException {
        return resolve("pdfimages", "register.pdfimages.path");
    }

    public static String pdfinfo() throws IOException {
        return resolve("pdfinfo", "register.pdfinfo.path");
    }

    public static String jbig2() throws IOException {
        return resolve("jbig2", "register.jbig2.path");
    }

    /**
     * The tool path via the shared {@link io.github.p4suta.shared.process.ToolPath}: {@code
     * -D<property>} if set, else the first executable named {@code tool} on PATH. A missing tool is
     * fatal here, so this throws.
     */
    static String resolve(String tool, String property) throws IOException {
        return ToolPath.resolve(tool, property)
                .map(Path::toString)
                .orElseThrow(
                        () ->
                                RegisterException.withDetail(
                                        RegisterErrorKind.NATIVE_TOOL_FAILED,
                                        tool
                                                + " not found on PATH; install it or set -D"
                                                + property
                                                + "=/path/to/"
                                                + tool,
                                        null));
    }

    /** Run a command, discarding all output, failing on nonzero exit or timeout. */
    public static void run(List<String> command, long timeoutSeconds) throws IOException {
        try {
            ProcessRunner.run(command, Duration.ofSeconds(timeoutSeconds));
        } catch (TimeoutException e) {
            throw RegisterException.withDetail(
                    RegisterErrorKind.NATIVE_TOOL_FAILED,
                    command.get(0) + " timed out after " + timeoutSeconds + "s",
                    null);
        } catch (IOException e) {
            throw RegisterException.withDetail(
                    RegisterErrorKind.NATIVE_TOOL_FAILED,
                    Objects.requireNonNullElse(e.getMessage(), command.get(0) + " failed"),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(command.get(0) + " was interrupted", e);
        }
    }

    /**
     * Run a command and return its stdout bytes, failing on nonzero exit or timeout. Kept raw (not
     * routed through {@link io.github.p4suta.shared.process.ProcessRunner}, whose {@code Result}
     * decodes output as UTF-8): the captured stdout can be a binary JBIG2 stream that must survive
     * byte-for-byte, so a String round-trip is unacceptable here.
     */
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
                throw RegisterException.withDetail(
                        RegisterErrorKind.NATIVE_TOOL_FAILED,
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
            throw RegisterException.withDetail(
                    RegisterErrorKind.NATIVE_TOOL_FAILED,
                    command.get(0) + " failed with exit code " + code,
                    null);
        }
    }
}
