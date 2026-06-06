package io.github.p4suta.webapp.infrastructure;

import io.github.p4suta.webapp.port.ConversionExecutor;
import io.github.p4suta.webapp.port.QueueFullException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs at most one conversion at a time over a bounded queue. One worker is deliberate: each
 * pdfbook already fans out across the machine's cores with its own {@code -j} workers, so a second
 * concurrent conversion would only oversubscribe the CPU. When the queue is full {@link
 * #submit(Runnable)} raises {@link QueueFullException} (the web layer answers HTTP 429) rather than
 * accept unbounded work.
 */
public final class BoundedConversionExecutor implements ConversionExecutor, AutoCloseable {

    private final ThreadPoolExecutor pool;

    /**
     * @param queueCapacity how many conversions may wait while one runs (at least one)
     */
    public BoundedConversionExecutor(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive: " + queueCapacity);
        }
        this.pool =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(queueCapacity),
                        new WorkerThreadFactory());
    }

    @Override
    public void submit(Runnable task) {
        try {
            pool.execute(task);
        } catch (RejectedExecutionException e) {
            throw new QueueFullException("conversion queue is full");
        }
    }

    /** Stops accepting work and lets the running and queued conversions finish. */
    @Override
    public void close() {
        pool.shutdown();
    }

    private static final class WorkerThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pdfbook-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
