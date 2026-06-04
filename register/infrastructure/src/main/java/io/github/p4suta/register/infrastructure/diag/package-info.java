/**
 * Development-time diagnostics for the registration pipeline: an opt-in ({@code --diag}) subsystem
 * that records and visualizes each page's internal state — skew, column detection, the reference it
 * aligns to, and its final clamped placement — as overlay images, projection-profile plots, a
 * machine-readable JSONL log, and an aggregate summary. It never affects a normal run.
 *
 * <p>Unlike the Leptonica registrar, which renders through {@code Pix}, this package draws with the
 * Java 2D API (so it can freely paint colored boxes, lines, text and plots), reading the rendered
 * page back as a {@link java.awt.image.BufferedImage}. It is the {@link
 * io.github.p4suta.register.port.Reporter} adapter, built via {@link
 * io.github.p4suta.register.infrastructure.diag.DiagnosticsReporterFactory}.
 */
@NullMarked
package io.github.p4suta.register.infrastructure.diag;

import org.jspecify.annotations.NullMarked;
