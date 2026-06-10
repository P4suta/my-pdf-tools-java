package io.github.p4suta.webapp.app;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the SPA's liveness heartbeat ({@code POST /api/v1/heartbeat}) and forwards it to the
 * desktop idle watcher, which stops the server once the browser stops beating (see {@link
 * DesktopIdleMonitor}).
 *
 * <p>Mapped in EVERY profile on purpose: the same SPA bundle ships in {@code prod}, where it still
 * beats — a {@code @Profile("!prod")} endpoint would 404 every few seconds per tab. Here the
 * watcher is injected as an {@link Optional} that is empty in {@code prod} (the bean is
 * {@code @Profile("!prod")}), so the endpoint is an inert {@code 204} there and the production
 * server never self-terminates.
 */
@RestController
@RequestMapping("/api/v1")
class HeartbeatController {

    private final Optional<DesktopIdleMonitor> monitor;

    HeartbeatController(Optional<DesktopIdleMonitor> monitor) {
        this.monitor = monitor;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat() {
        monitor.ifPresent(DesktopIdleMonitor::recordHeartbeat);
    }
}
