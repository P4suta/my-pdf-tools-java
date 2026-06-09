package io.github.p4suta.shared.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs an external process to completion, bounding the wait by a timeout and capturing stdout and
 * stderr separately.
 *
 * <p>The caller decides which non-zero exits are acceptable by passing a set of codes (e.g. {@code
 * qpdf}'s exit 3 for "success with warnings"). Exit {@code 0} is always acceptable; any other code
 * outside the set surfaces as an {@link IOException} carrying the code and the captured stderr.
 *
 * <p>The child's stdout and stderr are redirected to per-run temp files rather than read from
 * pipes, so a command that writes more than an OS pipe buffer holds (Linux ~64KB, Windows smaller)
 * cannot deadlock the wait — there is no pipe to fill. The files are read once the process exits
 * and deleted afterward.
 */
public final class ProcessRunner {

    private ProcessRunner() {}

    /**
     * The outcome of a finished process: its exit code, the bytes it wrote to stdout and stderr
     * (decoded as UTF-8), and how long the run took.
     *
     * @param exitCode the process exit value
     * @param stdout everything the process wrote to standard output
     * @param stderr everything the process wrote to standard error
     * @param elapsed wall-clock time from start to exit
     */
    public record Result(int exitCode, String stdout, String stderr, Duration elapsed) {}

    /**
     * Run {@code command}, accepting only a {@code 0} exit. Equivalent to {@link #run(List,
     * Duration, Set)} with an empty acceptable-codes set.
     *
     * @throws IOException if the process cannot be started, or exits non-zero
     * @throws TimeoutException if the process does not finish within {@code timeout} (it is killed)
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static Result run(List<String> command, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        return run(command, timeout, Set.of());
    }

    /**
     * Start {@code command}, wait up to {@code timeout} for it to finish, and return its exit code
     * and captured output. Exit {@code 0} and any code in {@code acceptableExitCodes} return a
     * {@link Result}; any other exit throws an {@link IOException} that includes the code and the
     * captured stderr.
     *
     * @param command the command and its arguments
     * @param timeout the maximum time to wait before the process is forcibly killed
     * @param acceptableExitCodes non-zero exit codes the caller treats as success (for example
     *     {@code qpdf}'s exit 3); {@code 0} is always acceptable
     * @return the exit code, captured stdout/stderr, and elapsed duration
     * @throws IOException if the process cannot be started, or exits with an unacceptable code
     * @throws TimeoutException if the process does not finish within {@code timeout} (it is killed)
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static Result run(
            List<String> command, Duration timeout, Set<Integer> acceptableExitCodes)
            throws IOException, InterruptedException, TimeoutException {
        long startNanos = System.nanoTime();
        Path outFile = Files.createTempFile("p4suta-proc-out-", ".tmp");
        Path errFile = Files.createTempFile("p4suta-proc-err-", ".tmp");
        // Redirect to files, not pipes: with no pipe to fill, the child never blocks on a write
        // while we wait, so a flood of stdout/stderr cannot deadlock (the old failure mode).
        Process process =
                new ProcessBuilder(command)
                        .redirectOutput(outFile.toFile())
                        .redirectError(errFile.toFile())
                        .start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TimeoutException(command.get(0) + " timed out after " + timeout);
            }
            // Decode leniently (replace malformed bytes) exactly as the old new String(bytes,
            // UTF_8)
            // did — Files.readString would instead throw on invalid UTF-8.
            String stdout = new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8);
            String stderr = new String(Files.readAllBytes(errFile), StandardCharsets.UTF_8);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            int exitCode = process.exitValue();
            if (exitCode != 0 && !acceptableExitCodes.contains(exitCode)) {
                throw new IOException(
                        command.get(0)
                                + " failed with exit code "
                                + exitCode
                                + ": "
                                + stderr.strip());
            }
            return new Result(exitCode, stdout, stderr, elapsed);
        } finally {
            // Kill the child if it is still running (timeout/interrupt) so the OS releases the temp
            // files (notably on Windows, where an open file cannot be deleted); a no-op once
            // exited.
            process.destroyForcibly();
            deleteQuietly(outFile);
            deleteQuietly(errFile);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Best-effort: a leftover temp file must never mask the real result or failure.
        }
    }
}
