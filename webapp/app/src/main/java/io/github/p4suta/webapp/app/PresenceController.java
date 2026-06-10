package io.github.p4suta.webapp.app;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds the browser's presence stream: the SPA opens one {@code GET /api/v1/presence} per tab and
 * keeps it for the page's lifetime, so the open connection IS the "a browser is here" signal that
 * the desktop auto-shutdown ({@link DesktopIdleMonitor}) watches — no client polling.
 *
 * <p>Desktop-only ({@code @Profile("!prod")}): the watcher exists only there. The SPA learns
 * whether to open this stream from {@link RuntimeInfoController}'s {@code autoShutdown} flag (false
 * in prod), so the reverse-proxied prod deployment opens zero presence connections and this
 * endpoint is never hit there.
 */
@RestController
@RequestMapping("/api/v1")
@Profile("!prod")
class PresenceController {

    private final DesktopIdleMonitor monitor;

    PresenceController(DesktopIdleMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/presence")
    SseEmitter presence() {
        return monitor.openPresence();
    }
}
