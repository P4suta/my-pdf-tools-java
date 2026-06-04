/**
 * The register-side Leptonica format mapping: {@link
 * io.github.p4suta.register.infrastructure.leptonica.LeptonicaFormats} resolves a domain output
 * format to the Leptonica {@code IFF_*} code {@code pixWrite} expects.
 *
 * <p>The Foreign Function &amp; Memory binding and its owning RAII {@code Pix} handle no longer
 * live here: they moved to the cross-app {@code io.github.p4suta.shared.imaging} island, which
 * exposes only PRIMITIVE pixel ops. The register-specific deskew POLICY layered on those primitives
 * lives in {@code io.github.p4suta.register.infrastructure.registrar} ({@code Deskewer}). What
 * remains in this package is the pure format-code mapping, which touches no native API.
 */
@NullMarked
package io.github.p4suta.register.infrastructure.leptonica;

import org.jspecify.annotations.NullMarked;
