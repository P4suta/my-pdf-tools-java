package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.application.DesktopShutdownPolicy;
import io.github.p4suta.webapp.domain.Job;
import io.github.p4suta.webapp.port.JobStore;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Desktop-only ({@code @Profile("!prod")}) auto-shutdown: the self-contained app-image stops when
 * the browser is closed, so the user never has to Ctrl+C the leftover console window.
 *
 * <p>The signal is the SSE connection itself, not a client poll: while a tab is open the SPA holds
 * one presence stream ({@link #openPresence()}, served at {@code GET /api/v1/presence}); when the
 * last one is gone the process shuts down after {@code presence-grace}, as long as no conversion is
 * running (a job started before the tab closed is left to finish).
 *
 * <p>Detecting that a stream dropped is where servlet SSE forces a periodic write: Tomcat does not
 * surface a client disconnect until the server next writes to that stream, so a single
 * {@code @Scheduled} tick sends each live stream a keep-alive comment and prunes the ones whose
 * write fails (the closed tabs) — then evaluates shutdown. So there is exactly ONE periodic
 * element, a server-side SSE keep-alive (the standard SSE mechanism, also what stops a proxy idling
 * the stream out); the client never polls. The grace absorbs a reload (a brief disconnect before
 * EventSource reconnects), and a finite {@code presence-timeout} bounds how long an abruptly-killed
 * browser's connection could linger if a write somehow never failed.
 *
 * <p>Absent in {@code prod} (the Docker/reverse-proxied runtime), so a client disconnect there can
 * never stop the server; {@link RuntimeInfoController} reports this bean's presence as the {@code
 * autoShutdown} flag, and the SPA opens the presence stream only when it is set, so prod opens zero
 * presence connections. Exit runs on a dedicated thread (see {@link #exitProcess()}).
 */
@Component
@Profile("!prod")
class DesktopIdleMonitor {

    private static final Logger log = LoggerFactory.getLogger(DesktopIdleMonitor.class);

    private final JobStore store;
    private final Clock clock;
    private final Duration grace;
    private final long presenceTimeoutMs;
    private final Runnable shutdown;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    // The live presence streams (one per open browser tab). Weakly-consistent iteration, so the
    // tick can prune while callbacks remove concurrently.
    private final Set<SseEmitter> streams = ConcurrentHashMap.newKeySet();

    // The instant the last stream dropped, or null while at least one is open (and initially,
    // before
    // any has ever connected) — so a headless run, with no presence stream ever, is never
    // auto-stopped. Written under the lock, read on the scheduler thread, so volatile.
    private volatile @Nullable Instant idleSince;

    @Autowired
    DesktopIdleMonitor(JobStore store, Clock clock, WebappProperties properties) {
        this(
                store,
                clock,
                properties.presenceGrace(),
                properties.presenceTimeout().toMillis(),
                DesktopIdleMonitor::exitProcess);
    }

    // Test seam: a controllable clock, a short grace, and a fake exit action. Tests drive
    // register()/deregister() with token emitters and call checkIdle() directly (the keep-alive
    // ping in tick() needs a servlet async context).
    DesktopIdleMonitor(JobStore store, Clock clock, Duration grace, Runnable shutdown) {
        this(store, clock, grace, Duration.ofMinutes(5).toMillis(), shutdown);
    }

    private DesktopIdleMonitor(
            JobStore store,
            Clock clock,
            Duration grace,
            long presenceTimeoutMs,
            Runnable shutdown) {
        this.store = store;
        this.clock = clock;
        this.grace = grace;
        this.presenceTimeoutMs = presenceTimeoutMs;
        this.shutdown = shutdown;
    }

    /**
     * Opens a presence stream for an open browser tab. Its existence marks the browser as here; the
     * tick prunes it once its write fails (tab closed) or it times out. The initial comment commits
     * the response so EventSource fires {@code onopen}.
     *
     * @return the SSE emitter to return from the controller
     */
    SseEmitter openPresence() {
        SseEmitter emitter = new SseEmitter(presenceTimeoutMs);
        register(emitter);
        emitter.onCompletion(() -> deregister(emitter));
        emitter.onError(error -> deregister(emitter));
        emitter.onTimeout(emitter::complete); // → onCompletion → deregister
        try {
            emitter.send(SseEmitter.event().comment("ready"));
        } catch (IOException e) {
            log.debug("presence stream closed before ready: {}", e.getMessage());
            deregister(emitter);
        }
        return emitter;
    }

    synchronized void register(SseEmitter emitter) {
        streams.add(emitter);
        idleSince = null;
    }

    synchronized void deregister(SseEmitter emitter) {
        if (streams.remove(emitter) && streams.isEmpty()) {
            idleSince = clock.instant();
        }
    }

    /**
     * One periodic tick: keep-alive every live stream (pruning the closed ones — the only way the
     * servlet surfaces a disconnect), then shut down if the browser has been gone past the grace
     * with nothing running. The keep-alive is server→client SSE plumbing; the client never polls.
     */
    @Scheduled(fixedDelayString = "${app.idle-check-interval-ms}")
    void tick() {
        pruneClosedStreams();
        checkIdle();
    }

    private void pruneClosedStreams() {
        for (SseEmitter emitter : streams) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | RuntimeException e) {
                // The write failed: the tab is gone. Drop it (and complete so the container
                // releases
                // the async request); deregister updates idleSince when this was the last stream.
                deregister(emitter);
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // already completing/closed
                }
            }
        }
    }

    /** The shutdown decision in isolation (no network), so it is unit-tested without the ping. */
    void checkIdle() {
        long active = store.all().stream().map(Job::state).filter(s -> !s.isTerminal()).count();
        // compareAndSet so the seam fires once even if a check overlaps the shutdown in progress.
        if (DesktopShutdownPolicy.shouldShutDown(idleSince, clock.instant(), grace, active)
                && shuttingDown.compareAndSet(false, true)) {
            log.info(
                    "browser disconnected (no presence for {}) and no active jobs — shutting down",
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
