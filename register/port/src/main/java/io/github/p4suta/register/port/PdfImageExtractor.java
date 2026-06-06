package io.github.p4suta.register.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * Extracts a scanned PDF's embedded bitonal images as TIFFs. The abstraction over {@code pdfimages}
 * / {@code pdfinfo}; the implementation ({@code infrastructure.pdf.PdfImagesExtractor}) keeps the
 * {@code ProcessBuilder} calls on its side of this boundary.
 */
public interface PdfImageExtractor {

    /**
     * The dominant x-ppi in DPI across the PDF's images (the scan resolution to register at).
     *
     * @throws IOException if the external tool fails
     */
    int dominantDpi(Path pdf) throws IOException;

    /**
     * Extract every page of {@code pdf} into {@code outDir} as TIFFs, parallelized over page-range
     * chunks across the worker pool.
     *
     * @param jobs the worker thread count (bounds the chunk count and pool slots used)
     * @throws IOException if the external tool fails
     */
    void extract(Path pdf, Path outDir, int jobs, ExecutorService pool) throws IOException;
}
