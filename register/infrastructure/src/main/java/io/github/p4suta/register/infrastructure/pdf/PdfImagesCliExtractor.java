package io.github.p4suta.register.infrastructure.pdf;

import io.github.p4suta.register.port.PdfImageExtractor;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Register's {@link PdfImageExtractor} adapter: a thin binding onto the cross-app {@link
 * io.github.p4suta.shared.pdf.PdfImagesCliExtractor} island (the Java port of {@code
 * extract-parallel.py} + {@code stamp-dpi.py --print}). The shared extractor splits the page range
 * across the worker pool (one {@code pdfimages -f/-l} per chunk, with distinct zero-padded {@code
 * page-cNN-} prefixes so a name sort yields reading order) and reads the dominant scan DPI from
 * {@code pdfimages -list} via the shared {@link io.github.p4suta.shared.pdf.PdfListingParser}. The
 * binaries it resolves are register's (the {@code register.pdfimages.path} / {@code
 * register.pdfinfo.path} override keys, passed at construction).
 *
 * <p>Stateless, so it is safe to share.
 */
public final class PdfImagesCliExtractor implements PdfImageExtractor {

    /**
     * Register's {@code -D} override keys pointing at its bundled {@code pdfimages}/{@code
     * pdfinfo}.
     */
    private static final String PDFIMAGES_PROPERTY_KEY = "register.pdfimages.path";

    private static final String PDFINFO_PROPERTY_KEY = "register.pdfinfo.path";

    private final io.github.p4suta.shared.pdf.PdfImagesCliExtractor delegate;

    public PdfImagesCliExtractor() {
        this.delegate =
                new io.github.p4suta.shared.pdf.PdfImagesCliExtractor(
                        PDFIMAGES_PROPERTY_KEY, PDFINFO_PROPERTY_KEY);
    }

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
