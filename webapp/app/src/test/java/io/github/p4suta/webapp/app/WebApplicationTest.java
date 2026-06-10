package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The pure startup-failure message shown when the server cannot boot. */
class WebApplicationTest {

    @Test
    void startupFailureMessagePointsAtTheErrorAndExplainsTheWait() {
        String message = WebApplication.startupFailureMessage();
        // Points the user at the diagnostic Spring already printed above, and explains why the
        // window is staying open (so a real-terminal launch is not mistaken for a hang).
        assertThat(message)
                .contains("pdfbook-web failed to start")
                .contains("Press Enter to close");
    }
}
