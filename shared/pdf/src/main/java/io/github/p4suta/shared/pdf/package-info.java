/**
 * PDF I/O adapters. No app domain exception is reachable here — a launch failure or an unacceptable
 * tool exit surfaces as a plain {@link java.io.IOException} — so this module depends on no app
 * module, only on {@code :shared:process}, PDFBox/xmpbox, and the SLF4J facade.
 *
 * <ul>
 *   <li>{@link io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler} packs a directory of cleaned
 *       bitonal pages into a lossless-JBIG2 PDF: each page is encoded by {@code jbig2 -p}
 *       (jbig2enc's lossless generic-region mode) in parallel and embedded verbatim as a {@code
 *       /JBIG2Decode} image XObject via PDFBox, with the source PDF's Info dict, XMP packet, and
 *       (&ge; 1.4) version inherited;
 *   <li>{@link io.github.p4suta.shared.pdf.PdfImagesCliExtractor} extracts a PDF's embedded bitonal
 *       images as TIFFs by driving {@code pdfimages}, splitting the page range across the pool; the
 *       textual {@code pdfinfo} / {@code pdfimages -list} reports are parsed by the pure parser
 *       below;
 *   <li>{@link io.github.p4suta.shared.pdf.PdfListingParser} is the pure (no PDFBox, no I/O) parser
 *       for {@code pdfinfo} / {@code pdfimages -list} text, including the dominant-DPI tie-break
 *       (ties resolve to the first value seen, a non-positive winner falls back to {@link
 *       io.github.p4suta.shared.pdf.PdfListingParser#DEFAULT_DPI});
 *   <li>{@link io.github.p4suta.shared.pdf.QpdfRunner} wraps {@code qpdf --linearize} (optionally
 *       {@code --min-version} / {@code --newline-before-endstream}) with bundled-binary resolution
 *       via {@code ToolPath}. It returns the {@link
 *       io.github.p4suta.shared.process.ProcessRunner.Result} (and propagates {@code
 *       IOException}/{@code TimeoutException}/{@code InterruptedException}); the failure policy
 *       (degrade-to-keep vs throw) is the caller's.
 * </ul>
 *
 * <p>The {@code jbig2} encode is the one call site that does not route through {@code
 * ProcessRunner}: {@code jbig2 -p} writes a raw binary JBIG2 stream to stdout, and the runner
 * decodes stdout to a UTF-8 {@link java.lang.String}, which corrupts binary bytes. So the assembler
 * uses a local {@link java.lang.ProcessBuilder} that redirects stdout straight to the per-page
 * scratch file. Everything else — tool resolution, the parallel fan-out, the text captures, the
 * {@code qpdf} run with its exit-3 tolerance — goes through {@code :shared:process}.
 */
@NullMarked
package io.github.p4suta.shared.pdf;

import org.jspecify.annotations.NullMarked;
