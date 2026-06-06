package io.github.p4suta.webapp.port;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.webapp.domain.JobId;

/**
 * Fans a job's progress events out to whoever is watching it (the web layer's SSE subscribers). The
 * application publishes into this port; how a subscriber receives the stream is the adapter's
 * concern.
 */
public interface ProgressPublisher {

    /**
     * Publishes one event for {@code id} to its current subscribers.
     *
     * @param id the job the event belongs to
     * @param event the event to publish
     */
    void publish(JobId id, ProgressEvent event);

    /**
     * Signals that {@code id} will emit no more events, so subscribers can close their streams.
     *
     * @param id the job whose stream is complete
     */
    void close(JobId id);
}
