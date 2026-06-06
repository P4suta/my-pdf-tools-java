/**
 * The web feature's Spring Boot front end and composition root — the only place Spring lives. It
 * holds the {@link io.github.p4suta.webapp.app.WebApplication} entry point, the {@link
 * io.github.p4suta.webapp.app.WebappConfig} that wires the framework-free use cases and adapters as
 * beans, the {@link io.github.p4suta.webapp.app.JobController} REST surface, the {@link
 * io.github.p4suta.webapp.app.SseProgressPublisher} (replay-on-subscribe progress fan-out), the
 * {@link io.github.p4suta.webapp.app.ReaperJob} schedule, and the request/response DTOs. The {@code
 * application} and {@code infrastructure} layers below it never see Spring.
 */
@NullMarked
package io.github.p4suta.webapp.app;

import org.jspecify.annotations.NullMarked;
