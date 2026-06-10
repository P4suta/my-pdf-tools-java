package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.webapp.domain.ConversionRequest;
import io.github.p4suta.webapp.domain.Direction;
import io.github.p4suta.webapp.domain.FirstPage;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.infrastructure.InMemoryJobStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The desktop auto-shutdown fires its shutdown seam (instead of killing the JVM) exactly when the
 * browser's presence stream is the signal: never while a stream is open or none ever connected,
 * after the grace once all streams have dropped, never while a conversion is in flight, and a
 * reconnect cancels a pending shutdown. Drives register()/deregister() with token emitters and
 * calls checkIdle() directly (tick()'s keep-alive ping needs a servlet async context).
 */
class DesktopIdleMonitorTest {

    private static final Instant T0 = Instant.parse("2026-06-10T00:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(15);
    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    private final MutableClock clock = new MutableClock(T0);
    private final InMemoryJobStore store = new InMemoryJobStore();
    private final AtomicInteger shutdowns = new AtomicInteger();
    private final DesktopIdleMonitor monitor =
            new DesktopIdleMonitor(store, clock, GRACE, shutdowns::incrementAndGet);

    @Test
    void doesNotShutDownWithNoConnectionEver() {
        clock.set(T0.plusSeconds(3600));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void doesNotShutDownWhileAStreamIsOpen() {
        monitor.register(new SseEmitter());
        clock.set(T0.plusSeconds(3600));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void shutsDownAfterGraceOnceAllStreamsDropped() {
        SseEmitter tab = new SseEmitter();
        monitor.register(tab);
        monitor.deregister(tab); // idleSince = T0
        clock.set(T0.plusSeconds(GRACE.toSeconds() + 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(1);
    }

    @Test
    void doesNotShutDownWithinGrace() {
        SseEmitter tab = new SseEmitter();
        monitor.register(tab);
        monitor.deregister(tab);
        clock.set(T0.plusSeconds(GRACE.toSeconds() - 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void doesNotShutDownWhileAConversionIsRunning() {
        store.save(Job.queued(new JobId("running-job"), REQUEST, "book.pdf", T0).toRunning());
        SseEmitter tab = new SseEmitter();
        monitor.register(tab);
        monitor.deregister(tab);
        clock.set(T0.plusSeconds(GRACE.toSeconds() + 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void reconnectBeforeGraceCancelsShutdown() {
        SseEmitter first = new SseEmitter();
        monitor.register(first);
        monitor.deregister(first); // idleSince = T0
        clock.set(T0.plusSeconds(5));
        monitor.register(new SseEmitter()); // a reload reconnected — idleSince cleared
        clock.set(T0.plusSeconds(GRACE.toSeconds() + 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void staysUpWhileAnyTabRemains() {
        SseEmitter tabA = new SseEmitter();
        monitor.register(tabA);
        monitor.register(new SseEmitter()); // tab B
        monitor.deregister(tabA); // tab A closed; B still open
        clock.set(T0.plusSeconds(GRACE.toSeconds() + 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    /**
     * A {@link Clock} whose instant the test advances between connect/disconnect and checkIdle().
     */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
