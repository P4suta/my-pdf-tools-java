package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The {@code prod} profile (the Docker runtime) must pin a fixed port. The default profile uses 0
 * (OS-assigned) for the double-clicked desktop image, but the container contract — {@code EXPOSE
 * 8080}, the {@code -p} publish, the reverse proxy — needs a known port, so application-prod.yml
 * overrides it back to 8080. This pins that precedence: profile-specific config wins over the
 * default. BrowserLauncher is {@code @Profile("!prod")}, so no browser opens here.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "app.pdfbook-binary=/bin/true",
            "app.work-dir=${java.io.tmpdir}/pdfbook-web-test/jobs"
        })
@ActiveProfiles("prod")
class ProdProfilePortTest {

    @Autowired Environment environment;

    @Test
    void prodProfilePinsPort8080() {
        assertThat(environment.getProperty("server.port")).isEqualTo("8080");
    }
}
