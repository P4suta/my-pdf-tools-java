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

/**
 * The desktop idle watcher fires its shutdown seam (instead of killing the JVM) exactly when the
 * policy says to: never before a heartbeat, after the grace with nothing running, and never while a
 * conversion is in flight.
 */
class DesktopIdleMonitorTest {

    private static final Instant T0 = Instant.parse("2026-06-10T00:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(20);
    private static final ConversionRequest REQUEST =
            new ConversionRequest(Direction.RTL, FirstPage.RIGHT, true, true, true, true, false, 2);

    private final MutableClock clock = new MutableClock(T0);
    private final InMemoryJobStore store = new InMemoryJobStore();
    private final AtomicInteger shutdowns = new AtomicInteger();
    private final DesktopIdleMonitor monitor =
            new DesktopIdleMonitor(store, clock, GRACE, shutdowns::incrementAndGet);

    @Test
    void doesNotShutDownBeforeAnyHeartbeat() {
        clock.set(T0.plusSeconds(3600));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void shutsDownAfterGraceWithNoActiveJobs() {
        monitor.recordHeartbeat(); // lastBeat = T0
        clock.set(T0.plusSeconds(GRACE.toSeconds() + 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(1);
    }

    @Test
    void doesNotShutDownWhileAConversionIsRunning() {
        store.save(Job.queued(new JobId("running-job"), REQUEST, "book.pdf", T0).toRunning());
        monitor.recordHeartbeat();
        clock.set(T0.plusSeconds(GRACE.toSeconds() + 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    @Test
    void doesNotShutDownWithinGrace() {
        monitor.recordHeartbeat();
        clock.set(T0.plusSeconds(GRACE.toSeconds() - 1));
        monitor.checkIdle();
        assertThat(shutdowns).hasValue(0);
    }

    /**
     * A {@link Clock} whose instant the test advances between recordHeartbeat() and checkIdle().
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
