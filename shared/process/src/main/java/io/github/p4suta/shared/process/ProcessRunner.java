package io.github.p4suta.shared.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * <p>The output streams are read after the process exits, so a command that floods a pipe beyond
 * the OS buffer could block; a flood trips the timeout (and a kill) rather than hanging forever.
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
        Process process = new ProcessBuilder(command).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException(command.get(0) + " timed out after " + timeout);
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        int exitCode = process.exitValue();
        if (exitCode != 0 && !acceptableExitCodes.contains(exitCode)) {
            throw new IOException(
                    command.get(0) + " failed with exit code " + exitCode + ": " + stderr.strip());
        }
        return new Result(exitCode, stdout, stderr, elapsed);
    }
}
