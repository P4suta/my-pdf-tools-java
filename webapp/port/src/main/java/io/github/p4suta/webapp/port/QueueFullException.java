package io.github.p4suta.webapp.port;

/**
 * Thrown by {@link ConversionExecutor#submit(Runnable)} when the executor is at capacity, so the
 * web layer answers "server busy" (HTTP 429) rather than queue work without bound.
 */
public final class QueueFullException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message describes the capacity that was exceeded
     */
    public QueueFullException(String message) {
        super(message);
    }
}
