package io.github.p4suta.webapp.application;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The framework-free decision behind the desktop app's "close the browser → the server stops"
 * behavior: should the self-contained app-image shut itself down right now?
 *
 * <p>Pure so it is exhaustively unit-tested; the scheduling, the heartbeat timestamp, and the
 * actual JVM exit live in the {@code :webapp:app} watcher that calls this. The rule is deliberately
 * conservative on three fronts:
 *
 * <ul>
 *   <li>a {@code null} {@code lastBeat} (the browser has never checked in — a headless run, or the
 *       window never opened) never triggers shutdown, so such a run is stopped only by Ctrl+C;
 *   <li>the {@code grace} after the last heartbeat absorbs a page reload or navigation (a brief gap
 *       between beats) without mistaking it for a closed browser;
 *   <li>an in-flight conversion ({@code activeJobs > 0}) keeps the server alive, so closing the tab
 *       mid-conversion lets the job finish rather than killing it.
 * </ul>
 */
public final class DesktopShutdownPolicy {

    private DesktopShutdownPolicy() {}

    /**
     * Whether the desktop server should shut down now.
     *
     * @param lastBeat the instant of the last browser heartbeat, or {@code null} if none has
     *     arrived
     * @param now the current instant
     * @param grace how long after the last heartbeat (with nothing running) to wait before stopping
     * @param activeJobs the number of non-terminal (QUEUED/RUNNING) jobs
     * @return {@code true} iff a heartbeat has been seen, the grace has elapsed, and nothing is
     *     running
     */
    public static boolean shouldShutDown(
            @Nullable Instant lastBeat, Instant now, Duration grace, long activeJobs) {
        return lastBeat != null && now.isAfter(lastBeat.plus(grace)) && activeJobs == 0;
    }
}
