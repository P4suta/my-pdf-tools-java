/**
 * The Leptonica-backed page registrar: the {@link io.github.p4suta.register.port.PageRegistrar}
 * adapter ({@link io.github.p4suta.register.infrastructure.registrar.LeptonicaPageRegistrar}) that
 * deskews, detects the main column ({@link
 * io.github.p4suta.register.infrastructure.registrar.MainColumnDetector}), and scales/places each
 * page using the domain's projection-profile, reference and transform-planner algorithms. The pixel
 * pushing that drives the {@code Pix} binding lives here, behind the {@code :port} boundary.
 */
@NullMarked
package io.github.p4suta.register.infrastructure.registrar;

import org.jspecify.annotations.NullMarked;
