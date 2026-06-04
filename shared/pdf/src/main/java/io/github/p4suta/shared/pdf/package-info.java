/**
 * The cross-app PDF I/O island: the read/encode/finish adapters every p4suta app (register,
 * despeckle, tate-yoko-pdf) would otherwise duplicate, generalized from despeckle's best-of-breed
 * PDF adapters. No app domain exception is reachable here — a launch failure or an unacceptable
 * tool exit surfaces as a plain {@link java.io.IOException} — so this module depends on no app
 * module, only on {@code :shared:process}, PDFBox/xmpbox, and the SLF4J facade.
 *
 * <ul>
 *   <li>{@link io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler} packs a directory of cleaned
 *       bitonal pages into a lossless-JBIG2 PDF: each page is encoded by {@code jbig2 -p}
 *       (jbig2enc's lossless generic-region mode) in parallel and embedded verbatim as a {@code
 *       /JBIG2Decode} image XObject via PDFBox, with the source PDF's Info dict, XMP packet, and
 *       (&ge; 1.4) version inherited opaquely. Despeckle's SUPERSET signature is the donor:
 *       per-page DPI, a {@link org.jspecify.annotations.Nullable} source, an {@link
 *       java.util.OptionalInt} forced DPI, and a caller-owned pool + scratch directory;
 *   <li>{@link io.github.p4suta.shared.pdf.PdfImagesCliExtractor} extracts a PDF's embedded bitonal
 *       images as TIFFs by driving {@code pdfimages}, splitting the page range across the pool; the
 *       textual {@code pdfinfo} / {@code pdfimages -list} reports are parsed by the pure parser
 *       below;
 *   <li>{@link io.github.p4suta.shared.pdf.PdfListingParser} is the PURE (no PDFBox, no I/O) parser
 *       for {@code pdfinfo} / {@code pdfimages -list} text, including the Python-parity
 *       dominant-DPI tie-break (ties resolve to the first value seen, a non-positive winner falls
 *       back to {@link io.github.p4suta.shared.pdf.PdfListingParser#DEFAULT_DPI}); lifted verbatim
 *       out of despeckle's {@code :domain};
 *   <li>{@link io.github.p4suta.shared.pdf.QpdfRunner} is a neutral capability over {@code qpdf
 *       --linearize} (optionally {@code --min-version} / {@code --newline-before-endstream}) with
 *       bundled-binary resolution via {@code ToolPath}. It RETURNS the shared {@link
 *       io.github.p4suta.shared.process.ProcessRunner.Result} (and propagates {@code
 *       IOException}/{@code TimeoutException}/{@code InterruptedException}); the failure POLICY
 *       (degrade-to-keep vs throw) is deliberately NOT decided here — each app wraps it.
 * </ul>
 *
 * <p>The {@code jbig2} encode is the one call site that does NOT route through {@code
 * ProcessRunner}: {@code jbig2 -p} writes a RAW binary JBIG2 stream to stdout, and the shared
 * runner decodes stdout to a UTF-8 {@link java.lang.String}, which corrupts binary bytes. So the
 * assembler keeps a single documented local {@link java.lang.ProcessBuilder} that redirects stdout
 * straight to the per-page scratch file. Everything else — tool resolution, the parallel fan-out,
 * the text captures, the {@code qpdf} run with its exit-3 tolerance — goes through {@code
 * :shared:process}.
 */
@NullMarked
package io.github.p4suta.shared.pdf;

import org.jspecify.annotations.NullMarked;
