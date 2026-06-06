package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.tateyokopdf.application.SpreadOptions;
import io.github.p4suta.tateyokopdf.application.SpreadService;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.PdfVersion;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.infrastructure.pdfbox.ImageDirDocumentFactory;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import java.nio.file.Path;

/**
 * The pipeline {@link Sink}: composes the registered bitonal pages into right-to-left two-page
 * spreads and writes the final PDF — the only repack in the run. Reuses tate's {@link
 * SpreadService} driven by the image-backed {@link ImageDirDocumentFactory}, which embeds each page
 * as CCITT G4 (register's lossless bytes pass through un-re-encoded), so no intermediate PDF is
 * produced.
 */
public final class SpreadPackSink implements Sink {

    private final ReadingDirection direction;
    private final FirstPageMode firstPageMode;
    private final boolean pdfA;
    private final MemoryMode memoryMode;
    private final DocumentMetadata metadata;

    /**
     * @param direction RTL (right-to-left) or LTR reading order
     * @param firstPageMode which side page one opens on
     * @param pdfA whether to emit PDF/A-2b conformance
     * @param memoryMode whether PDFBox caches output streams on the heap or in a temp file
     * @param metadata document metadata to carry onto the output (empty if none)
     */
    public SpreadPackSink(
            ReadingDirection direction,
            FirstPageMode firstPageMode,
            boolean pdfA,
            MemoryMode memoryMode,
            DocumentMetadata metadata) {
        this.direction = direction;
        this.firstPageMode = firstPageMode;
        this.pdfA = pdfA;
        this.memoryMode = memoryMode;
        this.metadata = metadata;
    }

    @Override
    public String name() {
        return "spread";
    }

    @Override
    public void write(Corpus input, Path output) {
        ImageDirDocumentFactory factory =
                new ImageDirDocumentFactory(
                        input.glob(), input.dpi(), metadata, PdfVersion.PDF_1_7, memoryMode);
        new SpreadService(
                        factory,
                        new SpreadLayoutCalculator(),
                        PdfPostProcessor.noOp(),
                        new SilentProgressListener())
                .execute(new SpreadOptions(input.dir(), output, direction, firstPageMode, pdfA));
    }
}
