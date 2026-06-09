package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The pure URL-resolution rules behind the startup browser-open. */
class BrowserLauncherTest {

    @Test
    void prefersTheActualBoundPortOverTheConfiguredOne() {
        // local.server.port reflects an ephemeral server.port=0; it wins when present.
        assertThat(BrowserLauncher.serverUrl("54321", "0")).isEqualTo("http://localhost:54321");
    }

    @Test
    void fallsBackToTheConfiguredPortWhenNoBoundPortIsPublished() {
        assertThat(BrowserLauncher.serverUrl(null, "8080")).isEqualTo("http://localhost:8080");
        assertThat(BrowserLauncher.serverUrl("", "8080")).isEqualTo("http://localhost:8080");
        assertThat(BrowserLauncher.serverUrl("  ", "8080")).isEqualTo("http://localhost:8080");
    }

    @Test
    void trimsWhitespaceAroundThePort() {
        assertThat(BrowserLauncher.serverUrl(" 8080 ", null)).isEqualTo("http://localhost:8080");
    }

    @Test
    void returnsNullWhenNoPortIsResolvable() {
        // The MOCK web environment (tests) publishes neither port.
        assertThat(BrowserLauncher.serverUrl(null, null)).isNull();
        assertThat(BrowserLauncher.serverUrl("", "")).isNull();
    }
}
