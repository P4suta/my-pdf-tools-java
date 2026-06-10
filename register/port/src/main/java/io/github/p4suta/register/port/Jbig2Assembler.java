package io.github.p4suta.register.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;
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
     * @param source a PDF whose Info dict, XMP and version are inherited, or {@code null} for none
     * @param forcedDpi a single DPI to size every page with, or empty to read each image's own
     * @param scratchDir directory for the intermediate per-page JBIG2 streams (caller owns
     *     lifecycle)
     * @throws IOException on a missing image, a failed external tool, or a write failure
     */
    void assemble(
            Path imageDir,
            Path outPdf,
            @Nullable Path source,
            OptionalInt forcedDpi,
            int jobs,
            Path scratchDir)
            throws IOException;
}
