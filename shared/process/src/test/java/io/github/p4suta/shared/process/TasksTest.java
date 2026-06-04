package io.github.p4suta.shared.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TasksTest {

    @Test
    void returnsResultsInSubmissionOrder() throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                int value = i;
                tasks.add(() -> value);
            }
            List<Integer> results = Tasks.awaitAll(pool, tasks, "interrupted", "failed");
            for (int i = 0; i < 16; i++) {
                assertEquals(i, results.get(i));
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void surfacesATasksIOExceptionUnchanged() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            IOException boom = new IOException("boom");
            Callable<Integer> task =
                    () -> {
                        throw boom;
                    };
            IOException thrown =
                    assertThrows(
                            IOException.class,
                            () -> Tasks.awaitAll(pool, List.of(task), "interrupted", "failed"));
            // The task's own IOException is re-thrown as-is, not wrapped with the failure message.
            assertSame(boom, thrown);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void wrapsANonIoFailureWithTheFailureMessage() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task =
                    () -> {
                        throw new IllegalStateException("oops");
                    };
            IOException thrown =
                    assertThrows(
                            IOException.class,
                            () -> Tasks.awaitAll(pool, List.of(task), "interrupted", "failed"));
            assertEquals("failed", thrown.getMessage());
            assertInstanceOf(IllegalStateException.class, thrown.getCause());
        } finally {
            pool.shutdown();
        }
    }
}
