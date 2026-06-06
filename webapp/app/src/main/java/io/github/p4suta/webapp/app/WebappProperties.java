package io.github.p4suta.webapp.app;

import java.nio.file.Path;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The web layer's configuration, bound from the {@code app.*} properties.
 *
 * @param workDir the base directory under which each job gets a private subdirectory
 * @param queueCapacity how many conversions may wait while one runs
 * @param conversionTimeout how long a single conversion may run before it is killed
 * @param jobTtl how long a job (its files and SSE buffer) lives after creation
 * @param reaperIntervalMs how often, in milliseconds, the reaper sweeps expired jobs
 * @param pdfbookBinary the pdfbook executable, or {@code null} to resolve it via {@code
 *     -Dp4suta.pdfbook.binary} or the {@code PATH}
 */
@ConfigurationProperties("app")
public record WebappProperties(
        Path workDir,
        int queueCapacity,
        Duration conversionTimeout,
        Duration jobTtl,
        long reaperIntervalMs,
        @Nullable Path pdfbookBinary) {}
