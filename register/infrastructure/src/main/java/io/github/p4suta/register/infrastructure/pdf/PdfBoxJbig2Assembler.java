package io.github.p4suta.register.infrastructure.pdf;

import io.github.p4suta.register.port.Jbig2Assembler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;

/**
 * Register's {@link Jbig2Assembler} adapter: a thin binding onto the cross-app {@link
 * io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler} island (the Java port of {@code jbig2-pdf.py} +
 * {@code pdfmeta.py}). The shared assembler encodes each page with {@code jbig2 -p} (lossless
 * generic-region mode) and embeds it verbatim as a {@code /JBIG2Decode} image XObject via PDFBox,
 * so the decoded pages stay bit-identical to the Python pipeline's; only the binary it resolves is
 * register's (the {@code register.jbig2.path} override key, passed at construction). PDFBox and the
 * {@code jbig2} process now live entirely on the shared island's side of this boundary.
 *
 * <p>Stateless, so it is safe to share.
 */
public final class PdfBoxJbig2Assembler implements Jbig2Assembler {

    /** Register's {@code -D} override key pointing at its bundled {@code jbig2} binary. */
    private static final String JBIG2_PROPERTY_KEY = "register.jbig2.path";

    private final io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler delegate;

    public PdfBoxJbig2Assembler() {
        this.delegate = new io.github.p4suta.shared.pdf.PdfBoxJbig2Assembler(JBIG2_PROPERTY_KEY);
    }

    /**
     * Assemble {@code imageDir}'s registered pages into {@code outPdf}, inheriting {@code source}'s
     * metadata and PDF version. The pipeline passes its resolved scan DPI as {@code
     * OptionalInt.of(dpi)} (forcing one size for every page) and a non-null source PDF, preserving
     * register's prior fixed-DPI, metadata-inheriting behavior.
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
