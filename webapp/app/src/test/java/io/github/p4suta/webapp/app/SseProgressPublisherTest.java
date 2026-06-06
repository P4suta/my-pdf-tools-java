package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.progress.JsonlProgressCodec;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseProgressPublisherTest {

    private static final JobId ID = new JobId("job-1");
    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    private CapturingSseEmitter emitter = new CapturingSseEmitter();
    private final SseProgressPublisher publisher = new SseProgressPublisher(() -> emitter);

    private static Job queued() {
        return Job.queued(ID, REQUEST, "scan.pdf", Instant.EPOCH);
    }

    private static String json(ProgressEvent event) {
        return JsonlProgressCodec.write(event);
    }

    @Test
    void deliversLiveEventsToASubscriberAndCompletesOnClose() {
        publisher.openStream(queued().toRunning());

        publisher.publish(ID, new ProgressEvent.RunStarted(1));
        publisher.publish(ID, new ProgressEvent.RunCompleted());
        publisher.close(ID);

        assertThat(emitter.sent)
                .containsExactly(
                        json(new ProgressEvent.RunStarted(1)),
                        json(new ProgressEvent.RunCompleted()));
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void replaysBufferedEventsToALateSubscriberThatMissedTheWholeRun() {
        // The race: every event is emitted and the stream closed BEFORE the client subscribes.
        publisher.publish(ID, new ProgressEvent.RunStarted(1));
        publisher.publish(ID, new ProgressEvent.RunCompleted());
        publisher.close(ID);

        publisher.openStream(queued().toRunning().toDone(Instant.EPOCH));

        assertThat(emitter.sent)
                .containsExactly(
                        json(new ProgressEvent.RunStarted(1)),
                        json(new ProgressEvent.RunCompleted()));
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void synthesizesACompletedEventForATerminalJobWithNoBuffer() {
        publisher.openStream(queued().toRunning().toDone(Instant.EPOCH));

        assertThat(emitter.sent).containsExactly(json(new ProgressEvent.RunCompleted()));
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void synthesizesAFailedEventForATerminalFailedJobWithNoBuffer() {
        Job failed = queued().toRunning().toFailed(Instant.EPOCH, "EXTRACT", "boom");

        publisher.openStream(failed);

        assertThat(emitter.sent)
                .containsExactly(json(new ProgressEvent.RunFailed("EXTRACT", "boom")));
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void forgetDropsTheBuffer() {
        publisher.publish(ID, new ProgressEvent.RunStarted(1));
        publisher.forget(ID);

        // A fresh subscription sees no buffered events; a terminal job synthesizes its outcome.
        publisher.openStream(queued().toRunning().toDone(Instant.EPOCH));

        assertThat(emitter.sent).containsExactly(json(new ProgressEvent.RunCompleted()));
    }

    /**
     * An {@link SseEmitter} that records sent data and completion instead of writing a response.
     */
    private static final class CapturingSseEmitter extends SseEmitter {

        final List<String> sent = new ArrayList<>();
        boolean completed;

        CapturingSseEmitter() {
            super(0L);
        }

        @Override
        public void send(Object object) {
            sent.add(object.toString());
        }

        @Override
        public void complete() {
            completed = true;
        }
    }
}
