package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Structural guarantee that the production server can never self-terminate on a client disconnect:
 * the desktop idle watcher is {@code @Profile("!prod")}, so under the {@code prod} profile the
 * {@link DesktopIdleMonitor} bean must be absent — the heartbeat endpoint then forwards to an empty
 * {@code Optional} and nothing ever shuts the server down. Mirrors {@code ProdProfilePortTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "app.pdfbook-binary=/bin/true",
            "app.work-dir=${java.io.tmpdir}/pdfbook-web-test/jobs"
        })
@ActiveProfiles("prod")
class DesktopShutdownProdProfileTest {

    @Autowired ApplicationContext context;

    @Test
    void prodHasNoIdleShutdownWatcher() {
        assertThat(context.getBeanNamesForType(DesktopIdleMonitor.class)).isEmpty();
    }
}
