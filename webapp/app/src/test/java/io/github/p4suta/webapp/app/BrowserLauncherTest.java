package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The pure console banner behind the startup "open in your browser" announcement. */
class BrowserLauncherTest {

    @Test
    void bannerCarriesTheUrlOnItsOwnLineSoTerminalsLinkifyIt() {
        String banner = BrowserLauncher.banner("http://localhost:8080");
        // The URL sits alone on a line (surrounded by line separators), which is what lets a
        // terminal turn it into a clickable link and the user copy it cleanly.
        assertThat(banner)
                .contains(
                        System.lineSeparator()
                                + "      http://localhost:8080"
                                + System.lineSeparator())
                .contains("pdfbook-web is ready");
    }

    @Test
    void bannerReflectsAnEphemeralPort() {
        assertThat(BrowserLauncher.banner("http://localhost:54321"))
                .contains("http://localhost:54321");
    }
}
