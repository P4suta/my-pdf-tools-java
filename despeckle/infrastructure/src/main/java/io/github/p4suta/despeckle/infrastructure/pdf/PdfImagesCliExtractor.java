package io.github.p4suta.despeckle.infrastructure.pdf;

import io.github.p4suta.despeckle.port.PdfImageExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * Despeckle's {@link PdfImageExtractor} adapter — a thin port-implementing wrapper over the shared
 * {@code io.github.p4suta.shared.pdf.PdfImagesCliExtractor}, the cross-app {@code pdfimages}/{@code
 * pdfinfo} driver donated from this app. The wrapper exists only to bind that neutral capability to
 * despeckle's {@code :port} abstraction and to fix despeckle's own tool-override property keys
 * ({@code despeckle.pdfimages.path}, {@code despeckle.pdfinfo.path}), so packaged app-image runs
 * keep resolving the bundled binaries.
 *
 * <p>All page-range chunking, {@code pdfimages -list}/{@code pdfinfo} parsing (now the shared
 * {@code PdfListingParser}), and parallel extraction live in the shared class; this adapter
 * forwards {@link #dominantDpi} and {@link #extract} unchanged.
 */
public final class PdfImagesCliExtractor implements PdfImageExtractor {

    // The shared adapter has the same simple name, so it is referenced by fully-qualified name
    // rather than imported (the import would collide with this class).
    private final io.github.p4suta.shared.pdf.PdfImagesCliExtractor delegate =
            new io.github.p4suta.shared.pdf.PdfImagesCliExtractor(
                    "despeckle.pdfimages.path", "despeckle.pdfinfo.path");

    /** Creates an extractor that shells out to the {@code pdfimages}/{@code pdfinfo} tools. */
    public PdfImagesCliExtractor() {}

    /** The dominant x-ppi across the PDF's images, via {@code pdfimages -list}. */
    @Override
    public int dominantDpi(Path pdf) throws IOException {
        return delegate.dominantDpi(pdf);
    }

    /**
     * Extract all pages of {@code pdf} into {@code outDir} as TIFFs, parallelized over page-range
     * chunks. {@code jobs} bounds both the chunk count and the pool slots used.
     */
    @Override
    public void extract(Path pdf, Path outDir, int jobs, ExecutorService pool) throws IOException {
        delegate.extract(pdf, outDir, jobs, pool);
    }
}
