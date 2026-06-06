package io.github.p4suta.webapp.app;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports {@code DOWN} if the resolved pdfbook binary is no longer an executable file: the server
 * is up but cannot convert. Catches a binary that vanished or lost its executable bit after
 * startup.
 */
final class PdfbookBinaryHealthIndicator implements HealthIndicator {

    private final Path binary;

    PdfbookBinaryHealthIndicator(Path binary) {
        this.binary = binary;
    }

    @Override
    public Health health() {
        boolean executable = Files.isExecutable(binary);
        return (executable ? Health.up() : Health.down())
                .withDetail("binary", binary.toString())
                .build();
    }
}
