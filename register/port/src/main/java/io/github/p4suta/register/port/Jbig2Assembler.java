package io.github.p4suta.register.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;

/**
 * Packs a directory of registered bitonal pages into a lossless-JBIG2 PDF. The abstraction over
 * {@code jbig2} + PDFBox; the implementation ({@code infrastructure.pdf.PdfBoxJbig2Assembler})
 * keeps PDFBox and the {@code jbig2} process calls on its side of this boundary.
 */
public interface Jbig2Assembler {

    /**
     * Assemble {@code imageDir}'s registered pages into {@code outPdf}.
     *
     * @param imageDir the directory of registered bitonal page images, in name (reading) order
     * @param outPdf the lossless-JBIG2 PDF to write
     * @param source a PDF whose Info dict, XMP and version are inherited, or {@code null} for none
     * @param forcedDpi a single DPI to size every page with, or empty to read each image's own
     * @param pool the worker pool to run the per-page JBIG2 encodes on
     * @param scratchDir a directory for the intermediate per-page JBIG2 streams (caller owns its
     *     lifecycle)
     * @throws IOException on a missing image, a failed external tool, or a write failure
     */
    void assemble(
            Path imageDir,
            Path outPdf,
            @Nullable Path source,
            OptionalInt forcedDpi,
            ExecutorService pool,
            Path scratchDir)
            throws IOException;
}
