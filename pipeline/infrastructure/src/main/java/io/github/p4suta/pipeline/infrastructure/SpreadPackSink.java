package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.tateyokopdf.application.ProgressListener;
import io.github.p4suta.tateyokopdf.application.SpreadOptions;
import io.github.p4suta.tateyokopdf.application.SpreadService;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.PdfVersion;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.infrastructure.pdfbox.ImageDirDocumentFactory;
import io.github.p4suta.tateyokopdf.infrastructure.qpdf.QpdfLinearizer;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import java.nio.file.Path;

/**
 * The pipeline {@link Sink}: composes the registered bitonal pages into right-to-left two-page
 * spreads and writes the final PDF — the only repack in the run. Reuses tate's {@link
 * SpreadService} driven by the image-backed {@link ImageDirDocumentFactory}, which embeds each page
 * as CCITT G4 (register's lossless bytes pass through un-re-encoded), so no intermediate PDF is
 * produced.
 *
 * <p>The same {@link QpdfLinearizer} post-processor the standalone tate CLI uses runs over the
 * final PDF, so the book is linearized (Fast Web View) just as a directly-converted one is; if qpdf
 * is unavailable it degrades to a no-op rather than failing the run.
 */
public final class SpreadPackSink implements Sink {

    private final ReadingDirection direction;
    private final FirstPageMode firstPageMode;
    private final boolean pdfA;
    private final MemoryMode memoryMode;
    private final DocumentMetadata metadata;
    private final ProgressSink progress;
    private final PdfPostProcessor postProcessor;

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
        this(direction, firstPageMode, pdfA, memoryMode, metadata, ProgressSink.NO_OP);
    }

    /**
     * @param direction RTL (right-to-left) or LTR reading order
     * @param firstPageMode which side page one opens on
     * @param pdfA whether to emit PDF/A-2b conformance
     * @param memoryMode whether PDFBox caches output streams on the heap or in a temp file
     * @param metadata document metadata to carry onto the output (empty if none)
     * @param progress sink that each finished spread is reported into as a {@code PageProcessed}
     *     event (progress here advances over spreads, ≈ pageCount / 2)
     */
    public SpreadPackSink(
            ReadingDirection direction,
            FirstPageMode firstPageMode,
            boolean pdfA,
            MemoryMode memoryMode,
            DocumentMetadata metadata,
            ProgressSink progress) {
        this.direction = direction;
        this.firstPageMode = firstPageMode;
        this.pdfA = pdfA;
        this.memoryMode = memoryMode;
        this.metadata = metadata;
        this.progress = progress;
        // Resolve qpdf once (bundle -> PATH -> no-op fallback), exactly as tate's composition root
        // does, so the spread is linearized for Fast Web View.
        this.postProcessor = QpdfLinearizer.create();
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
                        postProcessor,
                        new SpreadProgressBridge())
                .execute(new SpreadOptions(input.dir(), output, direction, firstPageMode, pdfA));
    }

    /**
     * Bridges tate's per-spread {@link ProgressListener} onto this sink's progress channel: each
     * finished spread becomes a {@code PageProcessed} event labelled with {@link #name()}. The
     * start/complete callbacks carry nothing the channel needs (the stage boundary is emitted by
     * {@code PipelineRunner}), so they are ignored.
     */
    private final class SpreadProgressBridge implements ProgressListener {

        @Override
        public void onStart(int totalSpreads) {
            // no-op: the stage boundary is emitted by PipelineRunner.
        }

        @Override
        public void onSpreadComplete(int currentSpread, int totalSpreads) {
            progress.emit(new ProgressEvent.PageProcessed(name(), currentSpread, totalSpreads));
        }

        @Override
        public void onComplete(long elapsedMillis) {
            // no-op: the stage boundary is emitted by PipelineRunner.
        }
    }
}
