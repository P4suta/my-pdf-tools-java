package io.github.p4suta.webapp.app;

import io.github.p4suta.shared.process.ToolPath;
import io.github.p4suta.webapp.application.Conversions;
import io.github.p4suta.webapp.application.JobReaper;
import io.github.p4suta.webapp.infrastructure.BoundedConversionExecutor;
import io.github.p4suta.webapp.infrastructure.FilesystemResultCache;
import io.github.p4suta.webapp.infrastructure.FilesystemWorkspace;
import io.github.p4suta.webapp.infrastructure.InMemoryJobStore;
import io.github.p4suta.webapp.infrastructure.SubprocessConversionEngine;
import io.github.p4suta.webapp.infrastructure.UuidJobIdGenerator;
import io.github.p4suta.webapp.port.ConversionEngine;
import io.github.p4suta.webapp.port.JobIdGenerator;
import io.github.p4suta.webapp.port.JobStore;
import io.github.p4suta.webapp.port.ResultCache;
import io.github.p4suta.webapp.port.Workspace;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The composition root: wires the framework-free use cases and adapters together as Spring beans.
 * The one place that knows every concrete type.
 */
@Configuration
@EnableConfigurationProperties(WebappProperties.class)
public class WebappConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    InMemoryJobStore jobStore() {
        return new InMemoryJobStore();
    }

    @Bean
    Workspace workspace(WebappProperties properties) throws IOException {
        Path root = properties.workDir();
        Files.createDirectories(root);
        return new FilesystemWorkspace(root);
    }

    @Bean
    FilesystemResultCache resultCache(WebappProperties properties) throws IOException {
        // A sibling of the per-job work dir, so a cached result hard-links into a job's workspace
        // (same filesystem) rather than being copied.
        Path cacheRoot = properties.workDir().resolveSibling("cache");
        Files.createDirectories(cacheRoot);
        return new FilesystemResultCache(cacheRoot);
    }

    @Bean
    BoundedConversionExecutor conversionExecutor(WebappProperties properties) {
        return new BoundedConversionExecutor(properties.queueCapacity());
    }

    @Bean
    JobIdGenerator jobIdGenerator() {
        return new UuidJobIdGenerator();
    }

    @Bean
    Path pdfbookBinary(WebappProperties properties) {
        return ToolPath.resolve("pdfbook", "p4suta.pdfbook.binary")
                .or(() -> Optional.ofNullable(properties.pdfbookBinary()))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "pdfbook binary not found; set -Dp4suta.pdfbook.binary,"
                                            + " app.pdfbook-binary, or put pdfbook on the PATH"));
    }

    @Bean
    ConversionEngine conversionEngine(
            Path pdfbookBinary, WebappProperties properties, MeterRegistry registry) {
        ConversionEngine engine =
                new SubprocessConversionEngine(pdfbookBinary, properties.conversionTimeout());
        // Times and tags each conversion success/failure (see MeteredConversionEngine).
        return new MeteredConversionEngine(engine, registry);
    }

    @Bean
    SseProgressPublisher sseProgressPublisher() {
        return new SseProgressPublisher();
    }

    @Bean
    Conversions conversions(
            JobStore store,
            ConversionEngine engine,
            BoundedConversionExecutor executor,
            Workspace workspace,
            ResultCache cache,
            SseProgressPublisher publisher,
            JobIdGenerator ids,
            Clock clock) {
        return new Conversions(store, engine, executor, workspace, cache, publisher, ids, clock);
    }

    @Bean
    JobReaper jobReaper(
            JobStore store, Workspace workspace, Clock clock, WebappProperties properties) {
        return new JobReaper(store, workspace, clock, properties.jobTtl());
    }

    @Bean
    MeterBinder pdfbookMetrics(BoundedConversionExecutor executor, JobStore store) {
        return new PdfbookMetrics(executor, store);
    }

    @Bean
    HealthIndicator pdfbookBinaryHealthIndicator(Path pdfbookBinary) {
        return new PdfbookBinaryHealthIndicator(pdfbookBinary);
    }

    @Bean
    HealthIndicator workDirHealthIndicator(WebappProperties properties) {
        return new WorkDirHealthIndicator(properties.workDir());
    }

    @Bean
    HealthIndicator queueHealthIndicator(BoundedConversionExecutor executor) {
        return new QueueHealthIndicator(executor);
    }
}
