/**
 * The pure algorithms of the registration kernel, operating only on the {@link
 * io.github.p4suta.register.domain.model domain value types} and primitive arrays — no Leptonica
 * {@code Pix}, no filesystem, no threads. The projection-profile reductions ({@link
 * io.github.p4suta.register.domain.service.ProjectionProfile}) that drive main-column detection;
 * the per-parity median {@link io.github.p4suta.register.domain.service.Reference} layout the
 * corpus is registered onto; and the per-page placement planner ({@link
 * io.github.p4suta.register.domain.service.TransformPlanner}). The pixel reading and pushing that
 * feed and apply these live in {@code :infrastructure}, behind the {@code :port} boundary.
 */
@NullMarked
package io.github.p4suta.register.domain.service;

import org.jspecify.annotations.NullMarked;
