package io.github.p4suta.webapp.app;

import io.github.p4suta.shared.process.ToolPath;
import io.github.p4suta.webapp.application.Conversions;
import io.github.p4suta.webapp.application.JobReaper;
import io.github.p4suta.webapp.infrastructure.BoundedConversionExecutor;
import io.github.p4suta.webapp.infrastructure.FilesystemWorkspace;
import io.github.p4suta.webapp.infrastructure.InMemoryJobStore;
import io.github.p4suta.webapp.infrastructure.SubprocessConversionEngine;
import io.github.p4suta.webapp.infrastructure.UuidJobIdGenerator;
import io.github.p4suta.webapp.port.ConversionEngine;
import io.github.p4suta.webapp.port.JobIdGenerator;
import io.github.p4suta.webapp.port.JobStore;
import io.github.p4suta.webapp.port.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The composition root: wires the framework-free use cases and adapters together as Spring beans.
 * This is the one place that knows every concrete type — the layers below are assembled here
 * exactly as the CLI's {@code PipelineCommand} assembles the pipeline, only with Spring holding the
 * references.
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
    BoundedConversionExecutor conversionExecutor(WebappProperties properties) {
        return new BoundedConversionExecutor(properties.queueCapacity());
    }

    @Bean
    JobIdGenerator jobIdGenerator() {
        return new UuidJobIdGenerator();
    }

    @Bean
    ConversionEngine conversionEngine(WebappProperties properties) {
        Path binary =
                ToolPath.resolve("pdfbook", "p4suta.pdfbook.binary")
                        .or(() -> Optional.ofNullable(properties.pdfbookBinary()))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "pdfbook binary not found; set"
                                                    + " -Dp4suta.pdfbook.binary,"
                                                    + " app.pdfbook-binary, or put pdfbook on the"
                                                    + " PATH"));
        return new SubprocessConversionEngine(binary, properties.conversionTimeout());
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
            SseProgressPublisher publisher,
            JobIdGenerator ids,
            Clock clock) {
        return new Conversions(store, engine, executor, workspace, publisher, ids, clock);
    }

    @Bean
    JobReaper jobReaper(
            JobStore store, Workspace workspace, Clock clock, WebappProperties properties) {
        return new JobReaper(store, workspace, clock, properties.jobTtl());
    }
}
