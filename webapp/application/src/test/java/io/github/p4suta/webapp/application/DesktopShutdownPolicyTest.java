package io.github.p4suta.webapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The pure "should the desktop server stop now?" decision. */
class DesktopShutdownPolicyTest {

    private static final Instant T0 = Instant.parse("2026-06-10T00:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(20);

    @Test
    void noShutdownWhileConnectedOrNeverConnected() {
        // null idleSince = a presence stream is open, or none ever connected (a headless run):
        // never
        // auto-stops — Ctrl+C handles the headless case.
        assertThat(DesktopShutdownPolicy.shouldShutDown(null, T0.plusSeconds(3600), GRACE, 0))
                .isFalse();
    }

    @Test
    void noShutdownWithinGrace() {
        // A reload/navigation gap is shorter than the grace, so it is not mistaken for a close.
        assertThat(DesktopShutdownPolicy.shouldShutDown(T0, T0.plusSeconds(10), GRACE, 0))
                .isFalse();
    }

    @Test
    void shutsDownAfterGraceWithNoActiveJobs() {
        assertThat(DesktopShutdownPolicy.shouldShutDown(T0, T0.plusSeconds(21), GRACE, 0)).isTrue();
    }

    @Test
    void noShutdownWhileAConversionIsRunning() {
        // The browser closed mid-conversion: let the in-flight job finish rather than killing it.
        assertThat(DesktopShutdownPolicy.shouldShutDown(T0, T0.plusSeconds(60), GRACE, 1))
                .isFalse();
    }

    @Test
    void graceBoundaryIsExclusive() {
        // Exactly idleSince+grace is not yet "after", so it must not shut down at the boundary.
        assertThat(DesktopShutdownPolicy.shouldShutDown(T0, T0.plus(GRACE), GRACE, 0)).isFalse();
        assertThat(DesktopShutdownPolicy.shouldShutDown(T0, T0.plus(GRACE).plusMillis(1), GRACE, 0))
                .isTrue();
    }
}
