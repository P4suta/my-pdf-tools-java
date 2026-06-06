/**
 * The web feature's pure domain core: the {@link io.github.p4suta.webapp.domain.Job} lifecycle
 * state machine and its {@link io.github.p4suta.webapp.domain.JobState}, the opaque {@link
 * io.github.p4suta.webapp.domain.JobId} identity, the validated conversion options ({@link
 * io.github.p4suta.webapp.domain.ConversionRequest} with {@link
 * io.github.p4suta.webapp.domain.Direction} / {@link io.github.p4suta.webapp.domain.FirstPage}),
 * and the typed {@link io.github.p4suta.webapp.domain.JobNotFoundException}. Framework-free and
 * I/O-free (no Spring, no {@code java.nio.file}, no pipeline types), so the web layer's business
 * rules are unit-tested in isolation.
 */
@NullMarked
package io.github.p4suta.webapp.domain;

import org.jspecify.annotations.NullMarked;
