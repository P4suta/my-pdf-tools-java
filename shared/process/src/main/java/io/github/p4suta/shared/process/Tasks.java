package io.github.p4suta.shared.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Runs a batch of {@link Callable}s on a caller-owned {@link ExecutorService} and collects their
 * results in submission order, turning the executor's checked machinery into a single {@link
 * IOException}.
 */
public final class Tasks {

    private Tasks() {}

    /**
     * Submit every task, wait for all of them, and return their results in the order {@code tasks}
     * was given. The first failure stops collection and surfaces as an {@link IOException}: a
     * task's own {@link IOException} is re-thrown unchanged (so callers see the real cause), any
     * other failure is wrapped with {@code failureMessage}, and an interruption restores the
     * thread's interrupt flag and is reported with {@code interruptedMessage}.
     *
     * @param pool the executor to run the tasks on (the caller owns its lifecycle)
     * @param tasks the work to run, one result per task
     * @param interruptedMessage the message for an {@link IOException} raised on interruption
     * @param failureMessage the message wrapping a non-{@code IOException} task failure
     * @param <T> the task result type
     * @return the task results, in submission order
     * @throws IOException if any task fails or the wait is interrupted
     */
    public static <T> List<T> awaitAll(
            ExecutorService pool,
            List<Callable<T>> tasks,
            String interruptedMessage,
            String failureMessage)
            throws IOException {
        List<Future<T>> futures;
        try {
            futures = pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(interruptedMessage, e);
        }
        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(interruptedMessage, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                throw new IOException(failureMessage, cause);
            }
        }
        return results;
    }
}
