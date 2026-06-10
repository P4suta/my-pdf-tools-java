package io.github.p4suta.shared.process;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * Runs a batch of {@link Callable}s on a batch-owned executor with fail-fast semantics: the first
 * failure interrupts the remaining workers, the batch waits until every worker has actually stopped
 * (quiescence), and only then does the failure propagate — so a caller's {@code finally} can safely
 * delete the directories the workers were writing into.
 *
 * <p>The propagated failure keeps its identity: a task's own {@link IOException} or {@link
 * RuntimeException} (the apps' domain exceptions are runtime exceptions) is re-thrown unchanged, so
 * the shared exception mapper still sees the original kind and maps the right exit code and {@code
 * RunFailed} token; an {@link UncheckedIOException} is unwrapped to its cause. This deliberately
 * mirrors {@code StructuredTaskScope.join()}'s contract (fail-fast, sibling interruption,
 * quiescence before propagation) on final-Java features, so a future swap to structured concurrency
 * stays internal to this class.
 */
public final class Tasks {

    private Tasks() {}

    /**
     * How one batch schedules its workers.
     *
     * <p>{@link #platform(int)} is for CPU-bound work (Leptonica/FFM pixel ops, TIFF G4 encoding):
     * a fixed pool of platform threads, because a long native downcall pins a virtual thread's
     * carrier, which would silently cap concurrency at the carrier count. {@link #virtual(int)} is
     * for subprocess-bound work (per-page {@code jbig2}, {@code pdfimages} chunks): one virtual
     * thread per task, with at most {@code maxInFlight} admitted past an internal semaphore so the
     * spawned child processes stay bounded.
     */
    public static final class Workers {

        private final int limit;
        private final boolean virtualThreads;

        private Workers(int limit, boolean virtualThreads) {
            if (limit < 1) {
                throw new IllegalArgumentException("worker limit must be >= 1, got " + limit);
            }
            this.limit = limit;
            this.virtualThreads = virtualThreads;
        }

        /** {@code jobs} platform threads — CPU-bound work. */
        public static Workers platform(int jobs) {
            return new Workers(jobs, false);
        }

        /**
         * A virtual thread per task, at most {@code maxInFlight} running at once — work that mostly
         * waits on a subprocess.
         */
        public static Workers virtual(int maxInFlight) {
            return new Workers(maxInFlight, true);
        }

        private ExecutorService newExecutor() {
            return virtualThreads
                    ? Executors.newVirtualThreadPerTaskExecutor()
                    : Executors.newFixedThreadPool(limit);
        }

        private @Nullable Semaphore newPermits() {
            return virtualThreads ? new Semaphore(limit) : null;
        }
    }

    /**
     * Per-item completion callback, invoked on the orchestrating thread in completion order: {@code
     * done} runs {@code 1..total}, strictly increasing, successes only — so progress events built
     * on it are ordered without any thread-safety burden on the listener.
     */
    @FunctionalInterface
    public interface ItemProgress {

        /** The no-op listener. */
        ItemProgress NONE = (done, total) -> {};

        /**
         * One more item finished.
         *
         * @param done how many items have finished so far (1-based, strictly increasing)
         * @param total how many items the batch runs
         */
        void onItem(int done, int total);
    }

    /** {@link #awaitAll(Workers, List, String, ItemProgress)} without progress reporting. */
    public static <T> List<T> awaitAll(Workers workers, List<Callable<T>> tasks, String label)
            throws IOException {
        return awaitAll(workers, tasks, label, ItemProgress.NONE);
    }

    /**
     * Run every task on a batch-owned executor and return their results in submission order.
     *
     * <p>On the first failure the remaining workers are interrupted ({@code shutdownNow}) and the
     * batch waits for full quiescence before the failure propagates with its identity intact:
     * {@link IOException} and {@link RuntimeException} unchanged, {@link UncheckedIOException}
     * unwrapped to its cause, anything else wrapped as {@code IOException(label + " failed")}.
     * Caller interruption likewise stops and joins the workers, restores the interrupt flag, and
     * surfaces as {@code IOException(label + " interrupted")}.
     *
     * @param workers the scheduling mode (see {@link Workers})
     * @param tasks the work to run, one result per task
     * @param label the batch's name in failure/interruption messages (e.g. {@code "G4 encode"})
     * @param progress called on this thread after each successful item
     * @param <T> the task result type
     * @return the task results, in submission order
     * @throws IOException if any task fails or the wait is interrupted
     */
    public static <T> List<T> awaitAll(
            Workers workers, List<Callable<T>> tasks, String label, ItemProgress progress)
            throws IOException {
        int total = tasks.size();
        List<@Nullable T> results = new ArrayList<>(Collections.nCopies(total, null));
        try (ExecutorService pool = workers.newExecutor()) {
            ExecutorCompletionService<IndexedResult<T>> completion =
                    new ExecutorCompletionService<>(pool);
            @Nullable Semaphore permits = workers.newPermits();
            // The fail-fast gate: set by the first worker whose task throws, checked by every
            // worker before it starts a task. shutdownNow() alone cannot stop a queued task a
            // newly-freed worker dequeues in the instant before the orchestrator reacts; the gate
            // closes that window (a gated task completes with the BatchCancelled marker instead
            // of running user code).
            AtomicBoolean failed = new AtomicBoolean();
            for (int i = 0; i < total; i++) {
                int index = i;
                Callable<T> task = tasks.get(i);
                completion.submit(
                        () -> {
                            if (failed.get()) {
                                throw new BatchCancelled();
                            }
                            // Acquired inside the worker so submission never blocks and an
                            // interrupt during the wait cancels cleanly.
                            if (permits != null) {
                                permits.acquire();
                            }
                            try {
                                return new IndexedResult<>(index, task.call());
                            } catch (Throwable t) {
                                failed.set(true);
                                throw t;
                            } finally {
                                if (permits != null) {
                                    permits.release();
                                }
                            }
                        });
            }
            try {
                int done = 0;
                while (done < total) {
                    Future<IndexedResult<T>> future = completion.take();
                    try {
                        IndexedResult<T> result = future.get();
                        results.set(result.index(), result.value());
                        done++;
                        progress.onItem(done, total);
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof BatchCancelled) {
                            // A gated sibling, not the root cause — keep draining: the failure
                            // that set the gate already ran, so its completion is on its way.
                            continue;
                        }
                        pool.shutdownNow();
                        throw failure(e.getCause(), label);
                    }
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IOException(label + " interrupted", e);
            }
            // The try-with-resources close() awaits full termination on every path, so by the
            // time a result or an exception reaches the caller, no worker is still running (or
            // still writing into directories the caller is about to delete).
        }
        return castNonNull(results);
    }

    /** One task's result tagged with its submission index, for submission-order assembly. */
    private record IndexedResult<T>(int index, @Nullable T value) {}

    /** The internal marker a gated (skipped-after-failure) task completes with; never user code. */
    private static final class BatchCancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BatchCancelled() {
            super("cancelled by an earlier failure in the batch", null, false, false);
        }
    }

    /** The failure to propagate for a task that threw {@code cause}; see {@link #awaitAll}. */
    private static IOException failure(@Nullable Throwable cause, String label) {
        switch (cause) {
            case IOException io -> {
                return io;
            }
            case UncheckedIOException unchecked -> {
                // UncheckedIOException's constructor requires a non-null cause.
                return java.util.Objects.requireNonNull(unchecked.getCause());
            }
            case RuntimeException runtime -> throw runtime;
            case Error error -> throw error;
            case null, default -> {
                return new IOException(label + " failed", cause);
            }
        }
    }

    /**
     * {@return the results list with every slot filled} Every slot was set exactly once (one
     * completion per submitted task), so the nullable build-up list is safe to expose as non-null.
     */
    @SuppressWarnings({"unchecked", "NullAway"}) // each index 0..total-1 was set by its task
    private static <T> List<T> castNonNull(List<@Nullable T> results) {
        return (List<T>) (List<?>) results;
    }
}
