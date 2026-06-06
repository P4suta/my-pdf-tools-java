/**
 * The web feature's driven ports: the interfaces the application layer calls and the infrastructure
 * adapters implement — {@link io.github.p4suta.webapp.port.JobStore}, {@link
 * io.github.p4suta.webapp.port.ConversionEngine}, {@link
 * io.github.p4suta.webapp.port.ConversionExecutor}, {@link io.github.p4suta.webapp.port.Workspace},
 * {@link io.github.p4suta.webapp.port.ProgressPublisher}, {@link
 * io.github.p4suta.webapp.port.JobIdGenerator} — plus the {@link
 * io.github.p4suta.webapp.port.QueueFullException} the executor raises at capacity. They speak the
 * domain vocabulary and the shared progress events only; no framework type crosses this boundary.
 */
@NullMarked
package io.github.p4suta.webapp.port;

import org.jspecify.annotations.NullMarked;
