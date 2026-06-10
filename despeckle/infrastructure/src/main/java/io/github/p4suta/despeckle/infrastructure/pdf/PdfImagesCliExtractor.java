package io.github.p4suta.despeckle.infrastructure.pdf;

import io.github.p4suta.despeckle.port.PdfImageExtractor;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Despeckle's {@link PdfImageExtractor} adapter — a wrapper over the shared {@code
 * io.github.p4suta.shared.pdf.PdfImagesCliExtractor} ({@code pdfimages}/{@code pdfinfo} driver). It
 * binds the shared capability to despeckle's {@code :port} abstraction and fixes the tool-override
 * keys ({@code despeckle.pdfimages.path}, {@code despeckle.pdfinfo.path}), so a packaged app-image
 * keeps resolving its bundled binaries.
 *
 * <p>The page-range chunking, {@code pdfimages -list}/{@code pdfinfo} parsing (the shared {@code
 * PdfListingParser}), and parallel extraction live in the shared class; this adapter forwards
 * {@link #dominantDpi} and {@link #extract} unchanged.
 */
public final class PdfImagesCliExtractor implements PdfImageExtractor {

    // The shared adapter has the same simple name, so it is referenced by fully-qualified name
    // rather than imported (the import would collide with this class).
    private final io.github.p4suta.shared.pdf.PdfImagesCliExtractor delegate =
            new io.github.p4suta.shared.pdf.PdfImagesCliExtractor(
                    "despeckle.pdfimages.path", "despeckle.pdfinfo.path");

    public PdfImagesCliExtractor() {}

    /** The dominant x-ppi across the PDF's images, via {@code pdfimages -list}. */
    @Override
    public int dominantDpi(Path pdf) throws IOException {
        return delegate.dominantDpi(pdf);
    }

    /**
     * Extract all pages of {@code pdf} into {@code outDir} as TIFFs, parallelized over page-range
     * chunks. at most {@code jobs} chunks run at once.
     */
    @Override
    public void extract(Path pdf, Path outDir, int jobs) throws IOException {
        delegate.extract(pdf, outDir, jobs);
    }
}
