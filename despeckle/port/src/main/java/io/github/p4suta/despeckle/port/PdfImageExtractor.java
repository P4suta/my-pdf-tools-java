package io.github.p4suta.despeckle.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * Extracts a PDF's embedded bitonal images, and reports its dominant scan resolution. The
 * abstraction over the {@code pdfimages}/{@code pdfinfo} drivers; the implementation ({@code
 * infrastructure.pdf.PdfImagesCliExtractor}) splits the page range across the supplied pool, one
 * {@code pdfimages} invocation per chunk.
 */
public interface PdfImageExtractor {

    /** The most common rounded x-ppi across the PDF's embedded images. */
    int dominantDpi(Path pdf) throws IOException;

    /**
     * Extract all pages of {@code pdf} into {@code outDir} as TIFFs, parallelized over page-range
     * chunks. {@code jobs} bounds both the chunk count and the pool slots used.
     */
    void extract(Path pdf, Path outDir, int jobs, ExecutorService pool) throws IOException;
}
