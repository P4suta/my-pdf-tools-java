/**
 * The hexagonal ports: the interfaces the {@code application} layer drives and the {@code
 * infrastructure} adapters implement. They speak only the {@code domain} vocabulary and file paths
 * — no Leptonica {@code Pix}, no PDFBox, no AWT image crosses this boundary — so the registration
 * and pipeline orchestration stay free of any adapter library. {@link
 * io.github.p4suta.register.port.PageRegistrar} abstracts the Leptonica pixel pipeline; {@link
 * io.github.p4suta.register.port.PdfImageExtractor} and {@link
 * io.github.p4suta.register.port.Jbig2Assembler} the PDF extraction and assembly; {@link
 * io.github.p4suta.register.port.Reporter} (built via {@link
 * io.github.p4suta.register.port.ReporterFactory}) the opt-in diagnostics sink.
 */
@NullMarked
package io.github.p4suta.register.port;

import org.jspecify.annotations.NullMarked;
