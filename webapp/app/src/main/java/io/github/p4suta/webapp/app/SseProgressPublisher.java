package io.github.p4suta.webapp.app;

import io.github.p4suta.shared.progress.JsonlProgressCodec;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.domain.JobState;
import io.github.p4suta.webapp.port.ProgressPublisher;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans each job's progress events out to its SSE subscribers, with replay-on-subscribe so a client
 * that connects after some (or all) events were emitted still sees them. Each job has a {@link
 * JobStream} holding the events published so far, the live emitters, and whether the run finished.
 * {@link #publish} and {@link #openStream} reach the same {@code JobStream} through {@code
 * computeIfAbsent} and synchronize on it, so a job that completes before the client subscribes does
 * not lose events: the subscriber replays the buffer and, if the run already completed, the stream
 * closes immediately.
 */
public final class SseProgressPublisher implements ProgressPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseProgressPublisher.class);
    private static final long SSE_TIMEOUT_MS = Duration.ofHours(1).toMillis();

    private final ConcurrentHashMap<JobId, JobStream> streams = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> emitterFactory;

    /** Creates a publisher whose streams never time out before a conversion can finish. */
    public SseProgressPublisher() {
        this(() -> new SseEmitter(SSE_TIMEOUT_MS));
    }

    SseProgressPublisher(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    @Override
    public void publish(JobId id, ProgressEvent event) {
        streams.computeIfAbsent(id, key -> new JobStream()).publish(event);
    }

    @Override
    public void close(JobId id) {
        JobStream stream = streams.get(id);
        if (stream != null) {
            stream.complete();
        }
    }

    /**
     * Opens an SSE stream for {@code job}: replays everything published so far, then either
     * completes immediately (the run has finished) or stays open for live events.
     *
     * @param job the job to stream
     * @return the emitter to return from the controller
     */
    public SseEmitter openStream(Job job) {
        SseEmitter emitter = emitterFactory.get();
        streams.computeIfAbsent(job.id(), key -> new JobStream()).subscribe(emitter, job);
        return emitter;
    }

    /**
     * Drops a job's buffered stream — called by the reaper when the job is removed.
     *
     * @param id the job to forget
     */
    public void forget(JobId id) {
        streams.remove(id);
    }

    private static final class JobStream {

        private final List<ProgressEvent> buffer = new ArrayList<>();
        private final List<SseEmitter> emitters = new ArrayList<>();
        private boolean completed;

        synchronized void publish(ProgressEvent event) {
            buffer.add(event);
            for (SseEmitter emitter : List.copyOf(emitters)) {
                send(emitter, event);
            }
        }

        synchronized void complete() {
            if (completed) {
                return;
            }
            completed = true;
            for (SseEmitter emitter : List.copyOf(emitters)) {
                completeQuietly(emitter);
            }
            emitters.clear();
        }

        synchronized void subscribe(SseEmitter emitter, Job job) {
            if (buffer.isEmpty() && !completed && job.state().isTerminal()) {
                // Buffer empty (forgotten job or restart) but the job already finished: synthesize
                // a
                // terminal event from the stored job so the client is not left hanging.
                send(emitter, terminalEventFor(job));
                completeQuietly(emitter);
                return;
            }
            for (ProgressEvent event : buffer) {
                send(emitter, event);
            }
            if (completed) {
                completeQuietly(emitter);
            } else {
                emitters.add(emitter);
                emitter.onCompletion(() -> remove(emitter));
                emitter.onTimeout(() -> remove(emitter));
                emitter.onError(error -> remove(emitter));
            }
        }

        private synchronized void remove(SseEmitter emitter) {
            emitters.remove(emitter);
        }

        private static void send(SseEmitter emitter, ProgressEvent event) {
            try {
                // A default "message" event whose data is the shared JSONL line; the SPA parses it
                // with JSON.parse on EventSource.onmessage.
                emitter.send(JsonlProgressCodec.write(event));
            } catch (IOException | IllegalStateException e) {
                // The client went away or the stream already closed; its callback removes it.
                log.debug("dropping SSE send: {}", e.getMessage());
            }
        }

        private static void completeQuietly(SseEmitter emitter) {
            try {
                emitter.complete();
            } catch (RuntimeException e) {
                log.debug("error completing SSE emitter: {}", e.getMessage());
            }
        }

        private static ProgressEvent terminalEventFor(Job job) {
            if (job.state() == JobState.DONE) {
                return new ProgressEvent.RunCompleted();
            }
            @Nullable String kind = job.errorKind();
            @Nullable String message = job.errorMessage();
            return new ProgressEvent.RunFailed(
                    kind != null ? kind : "ERROR", message != null ? message : "conversion failed");
        }
    }
}
