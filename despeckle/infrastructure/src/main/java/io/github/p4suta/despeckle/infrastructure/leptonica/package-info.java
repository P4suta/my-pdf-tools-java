/**
 * The Leptonica adapter side of despeckle. The Foreign Function &amp; Memory island — the binding
 * to the system Leptonica library and the owning {@code Pix} RAII handle — lives in the shared
 * {@code :shared:imaging} module ({@link io.github.p4suta.shared.imaging.Pix} / its package-private
 * {@code Leptonica}). What remains here is the despeckle-specific policy and wiring: {@link
 * io.github.p4suta.despeckle.infrastructure.leptonica.LeptonicaPageCleaner} implements the {@link
 * io.github.p4suta.despeckle.port.PageCleaner} port, composing the shared primitives into the
 * keep-larger-than speck filter and resolving the domain {@code OutputFormat} to a Leptonica {@code
 * IFF_*} write code. No {@code Pix} crosses the {@link io.github.p4suta.despeckle.port} boundary.
 */
@NullMarked
package io.github.p4suta.despeckle.infrastructure.leptonica;

import org.jspecify.annotations.NullMarked;
