package io.github.p4suta.webapp.app;

import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The web layer's configuration, bound from the {@code app.*} properties. {@code @Validated} makes
 * a non-positive {@code queueCapacity} or {@code reaperIntervalMs} fail fast at startup with a
 * clear message rather than misbehave later. (Bean Validation's {@code @NotNull} is intentionally
 * not used: it would collide with the single JSpecify nullness vocabulary the ArchUnit rule pins,
 * and the {@code Path}/{@code Duration} values are always supplied by {@code application.yml}.)
 *
 * @param workDir the base directory under which each job gets a private subdirectory
 * @param queueCapacity how many conversions may wait while one runs (at least one)
 * @param conversionTimeout how long a single conversion may run before it is killed
 * @param jobTtl how long a job (its files and SSE buffer) lives after creation
 * @param cacheTtl how long a cached result lives after it was last written (longer than {@code
 *     jobTtl}: the cache outlives the jobs that fill it so repeats stay fast)
 * @param reaperIntervalMs how often, in milliseconds, the reaper sweeps expired jobs and cache
 *     entries
 * @param pdfbookBinary the pdfbook executable, or {@code null} to resolve it via {@code
 *     -Dp4suta.pdfbook.binary} or the {@code PATH}
 */
@Validated
@ConfigurationProperties("app")
public record WebappProperties(
        Path workDir,
        @Positive int queueCapacity,
        Duration conversionTimeout,
        Duration jobTtl,
        Duration cacheTtl,
        @Positive long reaperIntervalMs,
        @Nullable Path pdfbookBinary) {}
