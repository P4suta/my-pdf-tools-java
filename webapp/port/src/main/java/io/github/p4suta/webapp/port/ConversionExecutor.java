package io.github.p4suta.webapp.port;

/**
 * Runs conversion tasks asynchronously under a bounded concurrency. The production adapter runs a
 * single task at a time, since each pdfbook already saturates the machine with its own worker
 * threads, so concurrent jobs would oversubscribe the CPU.
 */
public interface ConversionExecutor {

    /**
     * Submits {@code task} to run on a worker thread.
     *
     * @param task the conversion task
     * @throws QueueFullException if the executor is at capacity and cannot accept more work
     */
    void submit(Runnable task);
}
