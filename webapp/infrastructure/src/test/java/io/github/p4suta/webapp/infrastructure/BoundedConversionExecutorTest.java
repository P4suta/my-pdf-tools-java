package io.github.p4suta.webapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.webapp.port.QueueFullException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BoundedConversionExecutorTest {

    @Test
    void runsSubmittedTasks() throws InterruptedException {
        try (BoundedConversionExecutor executor = new BoundedConversionExecutor(4)) {
            CountDownLatch done = new CountDownLatch(1);
            executor.submit(done::countDown);
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rejectsWorkWhenTheQueueIsFull() throws InterruptedException {
        try (BoundedConversionExecutor executor = new BoundedConversionExecutor(1)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            executor.submit(
                    () -> {
                        started.countDown();
                        await(release);
                    });
            assertThat(started.await(5, TimeUnit.SECONDS))
                    .isTrue(); // the single worker is now busy

            executor.submit(() -> {}); // fills the queue (capacity 1)

            assertThatThrownBy(() -> executor.submit(() -> {}))
                    .isInstanceOf(QueueFullException.class)
                    .hasMessageContaining("queue is full");

            release.countDown();
        }
    }

    @Test
    void rejectsANonPositiveCapacity() {
        assertThatThrownBy(() -> new BoundedConversionExecutor(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueCapacity must be positive");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
