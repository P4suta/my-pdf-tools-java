package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.application.DesktopShutdownPolicy;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.port.JobStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Desktop-only ({@code @Profile("!prod")}) watcher that stops the self-contained app-image when the
 * browser is closed, so the user never has to Ctrl+C the leftover console window. The SPA POSTs
 * {@code /api/v1/heartbeat} while a tab is open ({@link HeartbeatController} forwards each to
 * {@link #recordHeartbeat()}); once the beats stop and no conversion is running, this exits the
 * process.
 *
 * <p>Mirrors {@link ReaperJob}: a thin {@code @Scheduled} bean that holds the schedule and
 * delegates the decision to the framework-free {@link DesktopShutdownPolicy}. It is absent in
 * {@code prod} (the Docker/reverse-proxied runtime), so a client disconnect there can never stop
 * the server.
 *
 * <p>The exit goes through a {@link Runnable} seam (default {@code System.exit(0)}) so a test can
 * assert that shutdown WOULD fire without killing the JVM. {@code System.exit(0)} runs Spring's
 * shutdown hook (graceful web stop) and exits cleanly; because this fires on a scheduler thread
 * long after {@code main()} returned, it never trips {@code WebApplication.main()}'s
 * startup-failure handler.
 */
@Component
@Profile("!prod")
class DesktopIdleMonitor {

    private static final Logger log = LoggerFactory.getLogger(DesktopIdleMonitor.class);

    private final JobStore store;
    private final Clock clock;
    private final Duration grace;
    private final Runnable shutdown;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    // null until the browser's first heartbeat; written on a request thread, read on the scheduler
    // thread, so volatile for visibility.
    private volatile @Nullable Instant lastBeat;

    @Autowired
    DesktopIdleMonitor(JobStore store, Clock clock, WebappProperties properties) {
        this(store, clock, properties.heartbeatGrace(), DesktopIdleMonitor::exitProcess);
    }

    // Test seam: a controllable clock, a short grace, and a fake exit action.
    DesktopIdleMonitor(JobStore store, Clock clock, Duration grace, Runnable shutdown) {
        this.store = store;
        this.clock = clock;
        this.grace = grace;
        this.shutdown = shutdown;
    }

    /** Records that the browser is still open; called for each {@code POST /api/v1/heartbeat}. */
    void recordHeartbeat() {
        lastBeat = clock.instant();
    }

    /**
     * Shuts the desktop server down once the browser has gone quiet (no heartbeat for {@code
     * grace}) and nothing is converting. No-op until the first heartbeat (a headless run is left
     * for Ctrl+C).
     */
    @Scheduled(fixedDelayString = "${app.idle-shutdown-interval-ms}")
    void checkIdle() {
        long active = store.all().stream().map(Job::state).filter(s -> !s.isTerminal()).count();
        // compareAndSet so the seam fires once even if a check overlaps the shutdown in progress.
        if (DesktopShutdownPolicy.shouldShutDown(lastBeat, clock.instant(), grace, active)
                && shuttingDown.compareAndSet(false, true)) {
            log.info(
                    "browser closed (no heartbeat for {}) and no active jobs — shutting down",
                    grace);
            shutdown.run();
        }
    }

    /**
     * Exits the JVM from a dedicated thread, NOT the scheduler thread this is invoked on: {@code
     * System.exit} runs the context shutdown hook, which stops the {@code @Scheduled} taskScheduler
     * — calling it on the scheduler thread would make that stop wait on itself until the 30s
     * lifecycle timeout. A separate thread lets the scheduler stop promptly (exit ~instant).
     */
    private static void exitProcess() {
        Thread t = new Thread(() -> System.exit(0), "desktop-idle-shutdown");
        t.start();
    }
}
