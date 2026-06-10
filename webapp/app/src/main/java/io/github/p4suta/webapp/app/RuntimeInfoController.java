package io.github.p4suta.webapp.app;

import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the SPA, in every profile, whether this is the auto-shutdown desktop build. The SPA opens
 * its presence stream ({@link PresenceController}) only when {@code autoShutdown} is true, so the
 * reverse-proxied prod deployment — where the {@link DesktopIdleMonitor} bean is absent
 * ({@code @Profile("!prod")}) — opens no presence connection and never self-terminates on a client
 * disconnect. Driving the flag off the bean's presence keeps it in lockstep with the watcher.
 */
@RestController
@RequestMapping("/api/v1")
class RuntimeInfoController {

    private final boolean autoShutdown;

    RuntimeInfoController(Optional<DesktopIdleMonitor> monitor) {
        this.autoShutdown = monitor.isPresent();
    }

    @GetMapping("/runtime")
    RuntimeInfo runtime() {
        return new RuntimeInfo(autoShutdown);
    }

    /**
     * @param autoShutdown whether the server stops itself when the browser closes (desktop build)
     */
    record RuntimeInfo(boolean autoShutdown) {}
}
