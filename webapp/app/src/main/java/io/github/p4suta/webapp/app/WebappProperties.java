package io.github.p4suta.webapp.app;

import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The web layer's configuration, bound from the {@code app.*} properties. {@code @Validated} makes
 * a non-positive {@code queueCapacity} or {@code reaperIntervalMs} fail fast at startup. Bean
 * Validation's {@code @NotNull} is not used: it would collide with the single JSpecify nullness
 * vocabulary the ArchUnit rule pins, and the {@code Path}/{@code Duration} values always come from
 * {@code application.yml}.
 *
 * @param workDir the base directory under which each job gets a private subdirectory
 * @param queueCapacity how many conversions may wait while one runs (at least one)
 * @param conversionTimeout how long a single conversion may run before it is killed
 * @param jobTtl how long a job (its files and SSE buffer) lives after creation
 * @param cacheTtl how long a cached result lives after it was last written (longer than {@code
 *     jobTtl}, so the cache outlives the jobs that fill it)
 * @param reaperIntervalMs how often, in milliseconds, the reaper sweeps expired jobs and cache
 *     entries
 * @param pdfbookBinary the pdfbook executable, or {@code null} to resolve it via {@code
 *     -Dp4suta.pdfbook.binary} or the {@code PATH}
 * @param presenceGrace desktop-only: how long after the browser's presence stream drops (with
 *     nothing running) the self-contained app-image waits before shutting itself down, so closing
 *     the browser ends the process. Inert in {@code prod} (the watcher bean is
 *     {@code @Profile("!prod")}). Keep comfortably above the EventSource reconnect delay (~3s) so a
 *     reload never trips shutdown.
 * @param presenceTimeout desktop-only: the lifetime of a presence SSE stream. A healthy tab's
 *     stream completes after this and EventSource transparently reconnects; it also bounds how long
 *     a half-open connection (an abruptly-killed browser that sent no FIN) lingers before it is
 *     reclaimed and shutdown can proceed.
 * @param idleCheckIntervalMs desktop-only: how often, in milliseconds, the local idle check runs
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
        @Nullable Path pdfbookBinary,
        Duration presenceGrace,
        Duration presenceTimeout,
        @Positive long idleCheckIntervalMs) {}
