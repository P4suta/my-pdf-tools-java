package io.github.p4suta.tateyokopdf.application;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.Validators;
import io.github.p4suta.tateyokopdf.domain.model.*;
import io.github.p4suta.tateyokopdf.domain.pagination.Pagination;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.port.*;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates one conversion: open the source, pair its pages via {@link Pagination} for the
 * {@link FirstPageMode}, lay out and write each spread, apply metadata, save, then post-process.
 *
 * <p>Pure orchestration over injected ports — it knows nothing about PDFBox or qpdf. Construct one
 * per conversion; it holds no conversion state beyond its collaborators.
 */
public class SpreadService {

    private static final Logger log = LoggerFactory.getLogger(SpreadService.class);

    private final DocumentFactory documentFactory;
    private final SpreadLayoutCalculator calculator;
    private final PdfPostProcessor pdfPostProcessor;
    private final ProgressListener progressListener;

    public SpreadService(
            DocumentFactory documentFactory,
            SpreadLayoutCalculator calculator,
            PdfPostProcessor pdfPostProcessor,
            ProgressListener progressListener) {
        this.documentFactory = documentFactory;
        this.calculator = calculator;
        this.pdfPostProcessor = pdfPostProcessor;
        this.progressListener = progressListener;
    }

    /**
     * Runs the conversion described by {@code options}: reads the source, writes one spread per
     * page pair, applies metadata (and PDF/A structure when requested), saves the output, then runs
     * the post-processor over the saved file.
     *
     * @param options the source, destination, and layout choices
     * @throws io.github.p4suta.tateyokopdf.domain.exception.SpreadException if the source is
     *     missing or unreadable, or the output cannot be written (e.g. {@code PDF_NOT_FOUND},
     *     {@code PDF_CORRUPTED}, {@code PDF_WRITE_FAILED})
     */
    public void execute(SpreadOptions options) {
        Validators.requireExists(options.sourcePath(), ErrorKind.PDF_NOT_FOUND);
        long startTime = System.currentTimeMillis();

        try (var source = documentFactory.openSource(options.sourcePath());
                var output = documentFactory.createOutput()) {

            int totalPages = source.pageCount();
            log.info("Source PDF: {} pages", totalPages);

            List<PagePairSpec> pairs = Pagination.paginate(options.firstPageMode(), totalPages);
            progressListener.onStart(pairs.size());

            for (int i = 0; i < pairs.size(); i++) {
                processSpread(source, output, pairs.get(i), options.direction());
                progressListener.onSpreadComplete(i + 1, pairs.size());
            }

            output.applyMetadata(source.metadata(), Instant.now(), Producer.NAME);
            if (options.pdfA()) {
                // After applyMetadata so the PDF/A XMP packet can mirror the info dictionary.
                output.finalizePdfA();
            }
            output.save(options.outputPath());
        }
        // Post-processing runs *after* the SpreadDocument is closed so the file
        // handle is released before qpdf opens it in --replace-input mode.
        pdfPostProcessor.process(options.outputPath());
        progressListener.onComplete(System.currentTimeMillis() - startTime);
    }

    private void processSpread(
            SourceDocument source,
            SpreadDocument output,
            PagePairSpec pairSpec,
            ReadingDirection direction) {

        switch (pairSpec) {
            case PagePairSpec.Pair(var first, var second) -> {
                PageDimension firstDim = source.pageDimension(first);
                PageDimension secondDim = source.pageDimension(second);

                SpreadLayout layout = calculator.calculate(direction, firstDim, secondDim);

                List<PagePlacement> placements =
                        List.of(
                                new PagePlacement(
                                        source.pageContent(first), layout.firstPosition()),
                                new PagePlacement(
                                        source.pageContent(second),
                                        layout.secondPosition().orElseThrow()));

                output.addSpread(layout.spec(), placements);
            }

            case PagePairSpec.Single(var pageIndex, var half) -> {
                PageDimension dim = source.pageDimension(pageIndex);

                SpreadLayout layout = calculator.calculateSingle(direction, dim, half);

                List<PagePlacement> placements =
                        List.of(
                                new PagePlacement(
                                        source.pageContent(pageIndex), layout.firstPosition()));

                output.addSpread(layout.spec(), placements);
            }
        }
    }
}
