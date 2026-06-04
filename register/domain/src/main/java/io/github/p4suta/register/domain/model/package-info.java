/**
 * The pure value types of the registration kernel: the page rectangle ({@link
 * io.github.p4suta.register.domain.model.Box}) and geometric transform ({@link
 * io.github.p4suta.register.domain.model.Transform}); the fixed output page ({@link
 * io.github.p4suta.register.domain.model.Canvas}) and paper model ({@link
 * io.github.p4suta.register.domain.model.PaperSize}); the placement strategy ({@link
 * io.github.p4suta.register.domain.model.Anchor}) and spread side ({@link
 * io.github.p4suta.register.domain.model.Parity}); the output format ({@link
 * io.github.p4suta.register.domain.model.OutputFormat}) and run knobs ({@link
 * io.github.p4suta.register.domain.model.RegisterOptions}); the analysis result ({@link
 * io.github.p4suta.register.domain.model.Detection}, {@link
 * io.github.p4suta.register.domain.model.Band}, {@link
 * io.github.p4suta.register.domain.model.PageObservation}); and the diagnostic record ({@link
 * io.github.p4suta.register.domain.model.PageDiagnostic}). Records and enums only — no behavior
 * that touches a framework, a file or the Leptonica binding.
 */
@NullMarked
package io.github.p4suta.register.domain.model;

import org.jspecify.annotations.NullMarked;
