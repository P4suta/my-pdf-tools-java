package io.github.p4suta.despeckle.infrastructure.pdf;

import io.github.p4suta.despeckle.port.Jbig2Assembler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;

/**
 * Despeckle's {@link Jbig2Assembler} adapter — a thin port-implementing wrapper over the shared
 * {@code io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler}, the cross-app {@code jbig2} + PDFBox
 * container donated from this app. The wrapper exists only to bind that neutral capability to
 * despeckle's {@code :port} abstraction and to fix despeckle's own {@code jbig2} override property
 * key ({@code despeckle.jbig2.path}), so a packaged app-image keeps resolving its bundled binary.
 *
 * <p>All per-page {@code jbig2 -p} encoding, the {@code /JBIG2Decode} XObject embedding, and the
 * Info/XMP/version inheritance live in the shared class; this adapter forwards {@link #assemble}
 * unchanged. Each page is sized by its own resolution unless the caller forces one DPI; a page that
 * carries no resolution falls back to the shared assembler's 300-dpi default.
 */
public final class PdfBoxJbig2Assembler implements Jbig2Assembler {

    // The shared adapter has the same simple name, so it is referenced by fully-qualified name
    // rather than imported (the import would collide with this class).
    private final io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler delegate =
            new io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler("despeckle.jbig2.path");

    /** Creates an assembler that encodes pages with {@code jbig2} and writes the PDF via PDFBox. */
    public PdfBoxJbig2Assembler() {}

    /**
     * Assemble {@code imageDir}'s cleaned pages into {@code outPdf}.
     *
     * @param imageDir the directory of cleaned bitonal pages (name order is reading order)
     * @param outPdf the lossless-JBIG2 PDF to write
     * @param source a PDF whose Info dict, XMP and version are inherited, or {@code null} for none
     * @param forcedDpi a single DPI to size every page with, or empty to read each image's own
     * @param pool the worker pool the per-page {@code jbig2} encodes run on
     * @param scratchDir scratch directory for the intermediate per-page JBIG2 streams
     *     (caller-owned)
     * @throws IOException if the directory is empty, a tool fails, or the write fails
     */
    @Override
    public void assemble(
            Path imageDir,
            Path outPdf,
            @Nullable Path source,
            OptionalInt forcedDpi,
            ExecutorService pool,
            Path scratchDir)
            throws IOException {
        delegate.assemble(imageDir, outPdf, source, forcedDpi, pool, scratchDir);
    }
}
