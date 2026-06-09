package io.github.p4suta.webapp.app;

import java.awt.Desktop;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * When the server is ready, makes the app reachable two ways — like a dev server: it prints a
 * clean, clickable {@code http://localhost:<port>} banner to the console, and (unless headless /
 * disabled) opens that URL in the default browser. This turns the double-clicked self-contained
 * app-image (the Docker-free Windows/macOS distribution) from "a console window where nothing
 * happens" into a real local web app.
 *
 * <p>The banner always prints, so even when auto-open can't run — headless servers, CI runners, or
 * {@code app.open-browser=false} — the user just clicks (or copies) the link. Auto-open is
 * additionally skipped in the {@code prod} profile (the Docker runtime is headless +
 * reverse-proxied, so this bean is {@code @Profile("!prod")} and not even created there). Opening
 * never affects the server: a failure is logged and the printed link still works.
 */
@Component
@Profile("!prod")
class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final boolean openBrowser;
    private final Environment environment;

    BrowserLauncher(
            @Value("${app.open-browser:true}") boolean openBrowser, Environment environment) {
        this.openBrowser = openBrowser;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        // local.server.port is the actual bound port Spring publishes once a real embedded server
        // is
        // up; it is absent under the MOCK web environment (tests), where there is nothing to open.
        String port = environment.getProperty("local.server.port");
        if (port == null || port.isBlank()) {
            return;
        }
        String url = "http://localhost:" + port.trim();
        System.out.print(banner(url));

        if (!openBrowser) {
            return; // the printed link is enough; user clicks it (or this is a CI/headless run)
        }
        if (!browseSupported()) {
            return; // no desktop browser (headless server) — the banner already showed the link
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            log.warn("Could not open the browser automatically — open {} manually.", url, e);
        }
    }

    /**
     * The console banner announcing the ready server, with the URL on its own indented line so
     * terminals linkify it and it is easy to click or copy. Pure, so it is unit-tested.
     *
     * @param url the local server URL
     * @return the multi-line banner text (leading/trailing blank lines for visibility)
     */
    static String banner(String url) {
        return System.lineSeparator()
                + "  pdfbook-web is ready — open it in your browser:"
                + System.lineSeparator()
                + System.lineSeparator()
                + "      "
                + url
                + System.lineSeparator()
                + System.lineSeparator();
    }

    private boolean browseSupported() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    }
}
