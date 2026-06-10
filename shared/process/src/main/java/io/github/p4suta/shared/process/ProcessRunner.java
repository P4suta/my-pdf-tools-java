package io.github.p4suta.shared.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * <p>Stdout and stderr are drained concurrently on virtual threads while the process runs, so a
 * command that writes more than an OS pipe buffer holds (Linux ~64KB, Windows smaller) cannot
 * deadlock the wait: neither pipe can fill while the child still has output to write. Reading the
 * streams only after {@code waitFor()} on a single thread — never draining during the run — is the
 * classic deadlock this avoids.
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
        // Drain both pipes concurrently, alongside the running process, so neither can fill up and
        // block the child. A virtual-thread-per-task executor keeps this near-free; try-with-
        // resources joins both drainers on the way out. On timeout, destroyForcibly closes the
        // streams, which lets the drainers reach EOF so close() does not hang.
        try (ExecutorService drainers = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> out = drainers.submit(() -> process.getInputStream().readAllBytes());
            Future<byte[]> err = drainers.submit(() -> process.getErrorStream().readAllBytes());
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new TimeoutException(command.get(0) + " timed out after " + timeout);
                }
            } catch (InterruptedException e) {
                // Kill the child before propagating, exactly like the timeout path: the drainers
                // then reach EOF, so the try-with-resources close() returns instead of waiting on
                // a live child's pipes — and no orphaned process outlives the interrupted caller.
                process.destroyForcibly();
                throw e;
            }
            // Decode leniently (replace malformed bytes), matching the long-standing behavior.
            String stdout = new String(await(out), StandardCharsets.UTF_8);
            String stderr = new String(await(err), StandardCharsets.UTF_8);
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
        }
    }

    /**
     * {@return the bytes a stream-drainer collected}, unwrapping the {@link ExecutionException} so
     * a read failure surfaces as the {@link IOException} it actually is.
     */
    private static byte[] await(Future<byte[]> drained) throws IOException, InterruptedException {
        try {
            return drained.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("failed to read process output", cause);
        }
    }
}
