package io.github.p4suta.webapp.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports {@code DOWN} if the resolved pdfbook binary is no longer a runnable file (the server is
 * up but cannot convert): a binary that vanished or, on POSIX, lost its executable bit after
 * startup.
 */
final class PdfbookBinaryHealthIndicator implements HealthIndicator {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private final Path binary;

    PdfbookBinaryHealthIndicator(Path binary) {
        this.binary = binary;
    }

    @Override
    public Health health() {
        // Files.isExecutable is unreliable on Windows (a present .exe is runnable by extension, but
        // the ACL probe can return false), so check existence there; on POSIX the exec bit is the
        // meaningful signal and the bundled image preserves it.
        boolean runnable = WINDOWS ? Files.isRegularFile(binary) : Files.isExecutable(binary);
        return (runnable ? Health.up() : Health.down())
                .withDetail("binary", binary.toString())
                .build();
    }
}
