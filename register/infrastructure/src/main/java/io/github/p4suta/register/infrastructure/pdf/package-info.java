/**
 * The PDF adapters: {@link io.github.p4suta.register.infrastructure.pdf.PdfImagesCliExtractor} (the
 * {@link io.github.p4suta.register.port.PdfImageExtractor}) and {@link
 * io.github.p4suta.register.infrastructure.pdf.PdfBoxJbig2Assembler} (the {@link
 * io.github.p4suta.register.port.Jbig2Assembler}). Both are thin bindings that implement register's
 * ports and delegate to the cross-app {@code io.github.p4suta.shared.pdf} island (the {@code
 * pdfimages}/{@code pdfinfo} extractor and the PDFBox + {@code jbig2} assembler), passing
 * register's own {@code -Dregister.<tool>.path} override keys. PDFBox and the {@code jbig2}/{@code
 * pdfimages} process calls live on the shared island's side of this boundary, so register's
 * production graph does not reference PDFBox directly.
 */
@NullMarked
package io.github.p4suta.register.infrastructure.pdf;

import org.jspecify.annotations.NullMarked;
