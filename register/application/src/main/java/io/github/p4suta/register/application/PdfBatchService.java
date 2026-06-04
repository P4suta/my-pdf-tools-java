package io.github.p4suta.register.application;

import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.domain.service.PdfOutputNaming;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the {@link PdfPipelineService} over every {@code *.pdf} directly under an input directory,
 * writing each registered result to {@code <outputDir>/<same-name>.pdf}. The files are processed
 * one at a time (each already saturates the cores through its own {@code --jobs} workers), and the
 * run is continue-on-error: a book that fails is logged and counted, never aborting the rest. An
 * output that already exists is skipped unless {@code force} is set, so an interrupted batch
 * resumes cheaply. Paper size and DPI are still detected per file (each book may differ).
 */
public final class PdfBatchService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfBatchService.class);

    private final PdfPipelineService pipeline;

    /**
     * Create a batch service over the injected single-PDF pipeline.
     *
     * @param pipeline the per-book PDF -> PDF pipeline
     */
    public PdfBatchService(PdfPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * One batch run.
     *
     * @param inputDir the directory whose top-level {@code *.pdf} files are the source scans
     * @param outputDir where each registered PDF is written (created if absent)
     * @param options registration knobs shared by every book (its empty {@code --dpi}/{@code
     *     --paper} are resolved per file)
     * @param jobs worker threads per file
     * @param force regenerate outputs that already exist instead of skipping them
     * @param suffix inserted before each output's {@code .pdf} extension (e.g. {@code _registered}
     *     turns {@code book.pdf} into {@code book_registered.pdf}); empty keeps the input name
     */
    public record Config(
            Path inputDir,
            Path outputDir,
            RegisterOptions options,
            int jobs,
            boolean force,
            String suffix) {}

    /**
     * What the batch did.
     *
     * @param ok books registered this run
     * @param skipped books whose output already existed (no {@code force})
     * @param failed books that threw (logged, not fatal)
     */
    public record Summary(int ok, int skipped, int failed) {}

    /**
     * Whether {@code input} should be processed as a batch — i.e. it is a directory of PDFs rather
     * than a single PDF. The filesystem probe lives in this orchestration class so the CLI can
     * route on it without touching {@code java.nio.file.Files} itself.
     *
     * @param input the CLI's first positional
     * @return true if {@code input} is a directory
     */
    public static boolean isBatchInput(Path input) {
        return Files.isDirectory(input);
    }

    /**
     * Register every top-level {@code *.pdf} under {@code inputDir} into {@code outputDir}.
     *
     * @param config the batch configuration
     * @return a count of registered / skipped / failed books
     * @throws IOException if the input cannot be listed or the output directory cannot be created
     */
    public Summary run(Config config) throws IOException {
        List<Path> inputs = listPdfs(config.inputDir());
        if (inputs.isEmpty()) {
            LOG.warn("no PDFs found in {}", config.inputDir());
            return new Summary(0, 0, 0);
        }
        Files.createDirectories(config.outputDir());

        int ok = 0;
        int skipped = 0;
        int failed = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Path input = inputs.get(i);
            Path output = config.outputDir().resolve(outputName(input, config.suffix()));
            String tag = "[" + (i + 1) + "/" + inputs.size() + "] " + input.getFileName();
            if (!config.force() && Files.exists(output)) {
                LOG.info("{} skip (exists)", tag);
                skipped++;
                continue;
            }
            LOG.info("{}", tag);
            try {
                pipeline.run(
                        new PdfPipelineService.Config(
                                input, output, config.options(), config.jobs(), config.force()));
                ok++;
            } catch (IOException | RuntimeException e) {
                // Continue-on-error: one bad scan must not sink the batch. RuntimeException catches
                // an unexpected failure deeper down (e.g. a Leptonica IllegalStateException); Error
                // still propagates.
                failed++;
                LOG.warn("{} failed: {}", tag, e.getMessage());
            }
        }
        LOG.info("done: {} ok, {} skipped, {} failed", ok, skipped, failed);
        return new Summary(ok, skipped, failed);
    }

    /**
     * The output file name for {@code input}: its stem plus {@code suffix} plus {@code .pdf}. An
     * empty suffix keeps the original name (extension case included); a non-empty suffix normalizes
     * the extension to lower-case {@code .pdf}. {@code input} is known to end in {@code .pdf}
     * (case-insensitive) because {@link #listPdfs} selected it. Package-private for unit testing.
     *
     * @param input a source PDF path
     * @param suffix the batch-mode output-name suffix ({@code ""} when none)
     * @return the output file name
     */
    static String outputName(Path input, String suffix) {
        return PdfOutputNaming.outputName(input, suffix);
    }

    /** Top-level {@code *.pdf} files under {@code dir} (case-insensitive), in file-name order. */
    static List<Path> listPdfs(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .filter(
                            p -> {
                                Path name = p.getFileName();
                                return name != null
                                        && name.toString()
                                                .toLowerCase(Locale.ROOT)
                                                .endsWith(".pdf");
                            })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
