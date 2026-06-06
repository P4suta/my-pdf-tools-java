package io.github.p4suta.webapp.app;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports {@code DOWN} if the per-job work directory is missing or not writable — without it no
 * upload can be stored and every conversion would fail.
 */
final class WorkDirHealthIndicator implements HealthIndicator {

    private final Path workDir;

    WorkDirHealthIndicator(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public Health health() {
        boolean writable = Files.isWritable(workDir);
        boolean ok = Files.isDirectory(workDir) && writable;
        return (ok ? Health.up() : Health.down())
                .withDetail("workDir", workDir.toString())
                .withDetail("writable", writable)
                .build();
    }
}
