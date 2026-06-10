package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * The whole bean graph wires: {@link WebappConfig}'s use cases and adapters, the {@code @Scheduled}
 * reaper, and Boot's web autoconfiguration all start in one mock servlet context. The pdfbook
 * binary is pointed at a present executable so the {@code conversionEngine} bean resolves; the work
 * dir is redirected to a throwaway temp subtree so the test never touches the real one.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "app.pdfbook-binary=/bin/true",
            "app.work-dir=${java.io.tmpdir}/pdfbook-web-test/jobs",
            // ApplicationReadyEvent fires in this @SpringBootTest too; keep BrowserLauncher from
            // launching a real browser on a developer's desktop during the build.
            "app.open-browser=false"
        })
class WebApplicationSmokeTest {

    @Autowired Environment environment;
    @Autowired RuntimeInfoController runtimeInfo;

    @Test
    void contextLoads() {
        // The test fails if the application context cannot be built (a missing bean, a bad
        // @ConfigurationProperties binding, a validation failure, ...).
    }

    @Test
    void defaultProfileBindsAnOsAssignedPort() {
        // The default (desktop) profile must request port 0 so the double-clicked app-image never
        // dies on a taken 8080; BrowserLauncher surfaces the real bound port at runtime.
        assertThat(environment.getProperty("server.port")).isEqualTo("0");
    }

    @Test
    void defaultProfileEnablesAutoShutdown() {
        // The desktop build reports autoShutdown so the SPA opens its presence stream and the
        // server stops when the browser closes (the DesktopIdleMonitor bean is present here).
        assertThat(runtimeInfo.runtime().autoShutdown()).isTrue();
    }
}
