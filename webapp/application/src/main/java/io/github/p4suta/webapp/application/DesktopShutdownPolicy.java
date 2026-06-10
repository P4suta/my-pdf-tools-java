package io.github.p4suta.webapp.application;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The framework-free decision behind the desktop app's "close the browser → the server stops"
 * behavior: should the self-contained app-image shut itself down right now?
 *
 * <p>Pure so it is exhaustively unit-tested; the SSE presence tracking, the timestamp, and the
 * actual JVM exit live in the {@code :webapp:app} watcher that calls this. The rule is deliberately
 * conservative on three fronts:
 *
 * <ul>
 *   <li>a {@code null} {@code idleSince} (a browser presence stream is currently open, or none has
 *       ever connected — a headless run) never triggers shutdown, so such a run is stopped only by
 *       Ctrl+C;
 *   <li>the {@code grace} after the last presence stream dropped absorbs a page reload (a brief
 *       disconnect before EventSource reconnects) without mistaking it for a closed browser;
 *   <li>an in-flight conversion ({@code activeJobs > 0}) keeps the server alive, so closing the tab
 *       mid-conversion lets the job finish rather than killing it.
 * </ul>
 */
public final class DesktopShutdownPolicy {

    private DesktopShutdownPolicy() {}

    /**
     * Whether the desktop server should shut down now.
     *
     * @param idleSince the instant the last browser presence stream dropped, or {@code null} while
     *     a stream is open (or none has ever connected)
     * @param now the current instant
     * @param grace how long after the browser went away (with nothing running) to wait before
     *     stopping
     * @param activeJobs the number of non-terminal (QUEUED/RUNNING) jobs
     * @return {@code true} iff the browser has been gone longer than the grace and nothing is
     *     running
     */
    public static boolean shouldShutDown(
            @Nullable Instant idleSince, Instant now, Duration grace, long activeJobs) {
        return idleSince != null && now.isAfter(idleSince.plus(grace)) && activeJobs == 0;
    }
}
