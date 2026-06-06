package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Source;
import io.github.p4suta.shared.pdf.PdfImagesCliExtractor;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The pipeline {@link Source}: extracts a scan PDF's bitonal pages once, via the shared {@code
 * pdfimages} extractor, into the working directory as {@code *.tif}. The resolved dominant DPI is
 * carried on the {@link Corpus} so every downstream stage uses the scan's true resolution. This is
 * the only PDF read in the run.
 */
public final class PdfExtractSource implements Source {

    /** Extracted page format produced by pdfimages -tiff. */
    private static final String EXTRACT_GLOB = "*.tif";

    private final Path sourcePdf;
    private final int jobs;
    private final PdfImagesCliExtractor extractor;

    /**
     * @param sourcePdf the scan PDF to extract
     * @param jobs worker threads for extraction
     */
    public PdfExtractSource(Path sourcePdf, int jobs) {
        this.sourcePdf = sourcePdf;
        this.jobs = jobs;
        // Tool resolution: -Dpipeline.pdfimages.path / -Dpipeline.pdfinfo.path override, else PATH.
        this.extractor =
                new PdfImagesCliExtractor("pipeline.pdfimages.path", "pipeline.pdfinfo.path");
    }

    @Override
    public String name() {
        return "extract";
    }

    @Override
    public Corpus open(Path workDir) throws IOException {
        int dpi = extractor.dominantDpi(sourcePdf);
        ExecutorService pool = Executors.newFixedThreadPool(jobs);
        try {
            extractor.extract(sourcePdf, workDir, jobs, pool);
        } finally {
            pool.shutdown();
        }
        return new Corpus(workDir, EXTRACT_GLOB, dpi, count(workDir, EXTRACT_GLOB));
    }

    private static int count(Path dir, String glob) throws IOException {
        int n = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path ignored : stream) {
                n++;
            }
        }
        return n;
    }
}
