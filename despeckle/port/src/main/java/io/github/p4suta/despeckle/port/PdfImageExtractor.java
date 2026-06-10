package io.github.p4suta.despeckle.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extracts a PDF's embedded bitonal images, and reports its dominant scan resolution. The
 * abstraction over the {@code pdfimages}/{@code pdfinfo} drivers; the implementation ({@code
 * infrastructure.pdf.PdfImagesCliExtractor}) splits the page range into bounded chunks, one {@code
 * pdfimages} invocation per chunk.
 */
public interface PdfImageExtractor {

    /** The most common rounded x-ppi across the PDF's embedded images. */
    int dominantDpi(Path pdf) throws IOException;

    /**
     * Extract all pages of {@code pdf} into {@code outDir} as TIFFs, parallelized over page-range
     * chunks. at most {@code jobs} chunks run at once.
     */
    void extract(Path pdf, Path outDir, int jobs) throws IOException;
}
