package io.github.p4suta.webapp.app;

import java.awt.Desktop;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Opens the default browser at the running server once it is ready, so double-clicking the
 * self-contained app-image launcher (the Docker-free Windows/macOS distribution) lands the user on
 * the web UI instead of just a console window. It is a no-op — logging the URL to open manually —
 * when there is no desktop browser (headless servers, CI runners), when disabled with {@code
 * app.open-browser=false}, or in the {@code prod} profile (the Docker runtime is headless and
 * reverse-proxied, so the bean is not even created there). A failure to open never affects the
 * server: the URL is always logged.
 */
@Component
@Profile("!prod")
class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final boolean enabled;
    private final Environment environment;

    BrowserLauncher(@Value("${app.open-browser:true}") boolean enabled, Environment environment) {
        this.enabled = enabled;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void openBrowser() {
        String url =
                serverUrl(
                        environment.getProperty("local.server.port"),
                        environment.getProperty("server.port"));
        if (url == null) {
            return; // no resolvable HTTP port (e.g. the MOCK web environment in tests)
        }
        if (!enabled) {
            log.info(
                    "Browser auto-open is off (app.open-browser=false). Open {} to use"
                            + " pdfbook-web.",
                    url);
            return;
        }
        if (!browseSupported()) {
            log.info("No desktop browser available. Open {} to use pdfbook-web.", url);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
            log.info("Opened {} in the default browser.", url);
        } catch (Exception e) {
            log.warn("Could not open the browser automatically — open {} manually.", url, e);
        }
    }

    /**
     * The local URL for the bound HTTP port: the actual {@code local.server.port} when set (it
     * reflects an ephemeral {@code server.port=0}), else the configured {@code server.port}, else
     * {@code null} when neither resolves (e.g. the MOCK web environment). Pure, so it is
     * unit-tested.
     *
     * @param localServerPort the actual bound port Spring publishes once the server is up, or null
     * @param configuredPort the {@code server.port} property, or null
     * @return {@code http://localhost:<port>}, or null when no port is resolvable
     */
    static @Nullable String serverUrl(
            @Nullable String localServerPort, @Nullable String configuredPort) {
        String port =
                (localServerPort != null && !localServerPort.isBlank())
                        ? localServerPort
                        : configuredPort;
        if (port == null || port.isBlank()) {
            return null;
        }
        return "http://localhost:" + port.trim();
    }

    private boolean browseSupported() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    }
}
