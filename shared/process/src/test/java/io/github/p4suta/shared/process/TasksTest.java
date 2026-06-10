package io.github.p4suta.shared.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The batch contract of {@link Tasks#awaitAll(Tasks.Workers, List, String, Tasks.ItemProgress)}:
 * submission-order results, fail-fast with sibling interruption, quiescence before the failure
 * propagates, exception identity, orchestrator-thread progress, and the virtual mode's in-flight
 * bound. All coordination is latch-based — no sleeps as logic.
 */
@Timeout(30)
class TasksTest {

    // ---- happy path ----

    @Test
    void returnsResultsInSubmissionOrder() throws IOException {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int value = i;
            tasks.add(() -> value);
        }
        List<Integer> results = Tasks.awaitAll(Tasks.Workers.platform(4), tasks, "batch");
        for (int i = 0; i < 16; i++) {
            assertEquals(i, results.get(i));
        }
    }

    @Test
    void progressRunsOnTheCallingThreadInCompletionOrder() throws IOException {
        Thread caller = Thread.currentThread();
        List<int[]> seen = new ArrayList<>(); // safe: appended on the calling thread only
        AtomicBoolean wrongThread = new AtomicBoolean();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int value = i;
            tasks.add(() -> value);
        }
        Tasks.awaitAll(
                Tasks.Workers.platform(4),
                tasks,
                "batch",
                (done, total) -> {
                    if (Thread.currentThread() != caller) {
                        wrongThread.set(true);
                    }
                    seen.add(new int[] {done, total});
                });
        assertFalse(wrongThread.get(), "progress must run on the orchestrating thread");
        assertEquals(8, seen.size());
        for (int i = 0; i < 8; i++) {
            assertEquals(i + 1, seen.get(i)[0], "done is strictly increasing from 1");
            assertEquals(8, seen.get(i)[1]);
        }
    }

    // ---- failure semantics ----

    @Test
    void surfacesATasksIOExceptionByIdentity() {
        IOException boom = new IOException("boom");
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () ->
                                Tasks.awaitAll(
                                        Tasks.Workers.platform(2),
                                        List.of(failing(boom)),
                                        "batch"));
        assertSame(boom, thrown);
    }

    @Test
    void surfacesARuntimeExceptionByIdentitySoErrorKindsSurvive() {
        IllegalStateException boom = new IllegalStateException("domain kind carrier");
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Tasks.awaitAll(
                                        Tasks.Workers.platform(2),
                                        List.of(failing(boom)),
                                        "batch"));
        assertSame(boom, thrown);
    }

    @Test
    void unwrapsAnUncheckedIOExceptionToItsCause() {
        IOException cause = new IOException("disk");
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () ->
                                Tasks.awaitAll(
                                        Tasks.Workers.platform(2),
                                        List.of(failing(new UncheckedIOException(cause))),
                                        "batch"));
        assertSame(cause, thrown);
    }

    @Test
    void wrapsAnUnknownCheckedFailureWithTheLabel() {
        Exception checked = new Exception("odd");
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () ->
                                Tasks.awaitAll(
                                        Tasks.Workers.platform(2),
                                        List.of(failing(checked)),
                                        "G4 encode"));
        assertEquals("G4 encode failed", thrown.getMessage());
        assertSame(checked, thrown.getCause());
    }

    // ---- fail-fast, sibling interruption, quiescence ----

    @Test
    void firstFailureInterruptsSiblingsAndSkipsQueuedWork() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        AtomicBoolean blockerInterrupted = new AtomicBoolean();
        AtomicBoolean queuedRan = new AtomicBoolean();
        IllegalStateException boom = new IllegalStateException("first failure");

        Callable<Void> blocker =
                () -> {
                    blockerStarted.countDown();
                    try {
                        new CountDownLatch(1).await(); // blocks until interrupted
                    } catch (InterruptedException e) {
                        blockerInterrupted.set(true);
                    }
                    return null;
                };
        Callable<Void> failing =
                () -> {
                    blockerStarted.await(); // fail only once the sibling is provably running
                    throw boom;
                };
        Callable<Void> queued =
                () -> {
                    queuedRan.set(true);
                    return null;
                };

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Tasks.awaitAll(
                                        Tasks.Workers.platform(2),
                                        List.of(blocker, failing, queued),
                                        "batch"));
        assertSame(boom, thrown);
        assertTrue(blockerInterrupted.get(), "the running sibling must be interrupted");
        assertFalse(queuedRan.get(), "queued work must be discarded on failure");
    }

    @Test
    void quiescesBeforeTheFailurePropagates() {
        AtomicInteger inFlight = new AtomicInteger();
        CountDownLatch blockerStarted = new CountDownLatch(1);

        Callable<Void> blocker =
                () -> {
                    inFlight.incrementAndGet();
                    try {
                        blockerStarted.countDown();
                        new CountDownLatch(1).await(); // until interrupted
                        return null;
                    } catch (InterruptedException e) {
                        return null;
                    } finally {
                        inFlight.decrementAndGet();
                    }
                };
        Callable<Void> failing =
                () -> {
                    blockerStarted.await();
                    throw new IllegalStateException("boom");
                };

        assertThrows(
                IllegalStateException.class,
                () -> Tasks.awaitAll(Tasks.Workers.platform(2), List.of(blocker, failing), "batch"));
        // The contract callers' finally-blocks rely on: when awaitAll throws, no worker is still
        // running (still writing into directories about to be deleted).
        assertEquals(0, inFlight.get());
    }

    @Test
    void progressCountsOnlySuccessesContiguously() {
        // Released from the PROGRESS callback (the orchestrating thread), not from the ok tasks:
        // a task-side latch would leave a window where the failing task's completion overtakes an
        // ok task's still-being-enqueued completion, making the recorded sequence racy.
        CountDownLatch twoConsumed = new CountDownLatch(2);
        List<Integer> dones = new ArrayList<>(); // appended on the calling thread only
        Callable<Void> ok = () -> null;
        Callable<Void> failing =
                () -> {
                    twoConsumed.await(); // both successes are consumed first, provably
                    throw new IllegalStateException("boom");
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        Tasks.awaitAll(
                                Tasks.Workers.platform(3),
                                List.of(ok, ok, failing),
                                "batch",
                                (done, total) -> {
                                    dones.add(done);
                                    twoConsumed.countDown();
                                }));
        assertEquals(List.of(1, 2), dones);
    }

    // ---- caller interruption ----

    @Test
    void callerInterruptionStopsWorkersAndRestoresTheFlag() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean flagRestored = new AtomicBoolean();

        Callable<Void> blocker =
                () -> {
                    inFlight.incrementAndGet();
                    try {
                        workerStarted.countDown();
                        new CountDownLatch(1).await();
                        return null;
                    } catch (InterruptedException e) {
                        workerInterrupted.set(true);
                        return null;
                    } finally {
                        inFlight.decrementAndGet();
                    }
                };

        Thread caller =
                new Thread(
                        () -> {
                            try {
                                Tasks.awaitAll(
                                        Tasks.Workers.platform(1), List.of(blocker), "register");
                            } catch (IOException e) {
                                thrown.set(e);
                                flagRestored.set(Thread.currentThread().isInterrupted());
                            }
                        });
        caller.start();
        assertTrue(workerStarted.await(10, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(10_000);
        assertFalse(caller.isAlive(), "the interrupted batch must unwind promptly");

        assertNotNull(thrown.get());
        assertInstanceOf(IOException.class, thrown.get());
        assertEquals("register interrupted", thrown.get().getMessage());
        assertTrue(flagRestored.get(), "the interrupt flag must be restored");
        assertTrue(workerInterrupted.get(), "the worker must be stopped");
        assertEquals(0, inFlight.get(), "quiescent before the exception reached the caller");
    }

    // ---- virtual mode ----

    @Test
    void virtualModeBoundsInFlightWork() throws Exception {
        int bound = 3;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger highWater = new AtomicInteger();
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch boundRunning = new CountDownLatch(bound);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(
                    () -> {
                        int now = inFlight.incrementAndGet();
                        highWater.accumulateAndGet(now, Math::max);
                        boundRunning.countDown();
                        try {
                            gate.await();
                            return null;
                        } finally {
                            inFlight.decrementAndGet();
                        }
                    });
        }
        Thread opener =
                new Thread(
                        () -> {
                            try {
                                // Open the gate only once `bound` workers are provably in flight,
                                // so the high-water assertion is meaningful, then let all pass.
                                boundRunning.await();
                                gate.countDown();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
        opener.start();
        Tasks.awaitAll(Tasks.Workers.virtual(bound), tasks, "jbig2 encode");
        opener.join(10_000);
        assertEquals(bound, highWater.get(), "no more than the bound may run at once");
    }

    @Test
    void rejectsANonPositiveWorkerLimit() {
        assertThrows(IllegalArgumentException.class, () -> Tasks.Workers.platform(0));
        assertThrows(IllegalArgumentException.class, () -> Tasks.Workers.virtual(-1));
    }

    /** A task that throws {@code failure} once called. */
    private static <T> Callable<T> failing(Exception failure) {
        return () -> {
            throw failure;
        };
    }
}
