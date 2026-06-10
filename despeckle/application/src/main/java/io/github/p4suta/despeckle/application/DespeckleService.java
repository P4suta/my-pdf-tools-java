package io.github.p4suta.despeckle.application;

import io.github.p4suta.despeckle.domain.model.OutputFormat;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.domain.model.ProcessResult;
import io.github.p4suta.despeckle.port.PageCleaner;
import io.github.p4suta.despeckle.port.Reporter;
import io.github.p4suta.despeckle.port.ReporterFactory;
import io.github.p4suta.shared.io.CorpusFiles;
import io.github.p4suta.shared.io.OutputDirs;
import io.github.p4suta.shared.kernel.PageProgressListener;
import io.github.p4suta.shared.process.Tasks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks an input directory, despeckles every matching page across a fixed thread pool, mirrors the
 * directory layout into the output directory, and optionally drives a {@link Reporter}.
 *
 * <p>This is the only place that touches the filesystem and threads, keeping the despeckle pipeline
 * a pure operation (reached through the {@link PageCleaner} port) that a future GUI could reuse
 * unchanged. The cleaner and the reporter factory are injected, so this service depends only on the
 * {@code port} and {@code domain} layers.
 */
public final class DespeckleService {

    private static final Logger LOG = LoggerFactory.getLogger(DespeckleService.class);

    private static final int PROGRESS_EVERY = 25;

    private final PageCleaner pageCleaner;
    private final ReporterFactory reporterFactory;

    /** Create a despeckle service over the injected adapters. */
    public DespeckleService(PageCleaner pageCleaner, ReporterFactory reporterFactory) {
        this.pageCleaner = pageCleaner;
        this.reporterFactory = reporterFactory;
    }

    /**
     * Configuration for one despeckle run.
     *
     * @param inputDir directory of source pages (walked recursively)
     * @param outputDir directory to mirror cleaned pages into
     * @param glob file-name glob for input selection
     * @param force whether to overwrite a non-empty output directory
     * @param reportDir report output directory, or {@code null} for no report
     * @param flipbook whether to assemble the animated-WebP overlay flip-book (needs {@code
     *     reportDir} and libwebp's {@code img2webp})
     */
    public record Config(
            Path inputDir,
            Path outputDir,
            OutputFormat format,
            String glob,
            int jobs,
            boolean force,
            ProcessOptions options,
            @Nullable Path reportDir,
            boolean flipbook) {}

    /**
     * Aggregate outcome of a run.
     *
     * @param pages number of pages processed
     * @param componentsRemoved total components removed across all pages; {@code 0} when no report
     *     consumed component stats (counting is skipped for speed without a report)
     * @param overRemovalWarnings number of pages flagged for possible over-removal
     */
    public record Summary(int pages, long componentsRemoved, int overRemovalWarnings) {}

    /** Execute a run, reporting no per-page progress. */
    public Summary run(Config config) throws IOException {
        return run(config, PageProgressListener.NO_OP);
    }

    /**
     * Execute a run, reporting each finished page to {@code progress}.
     *
     * @param progress called once per page as it completes (1-based count, total page count),
     *     always on the calling thread and in order
     */
    public Summary run(Config config, PageProgressListener progress) throws IOException {
        OutputDirs.prepare(config.outputDir(), config.force());

        List<Path> files = CorpusFiles.collect(config.inputDir(), config.glob());
        if (files.isEmpty()) {
            LOG.warn("no images matched {} under {}", config.glob(), config.inputDir());
            return new Summary(0, 0, 0);
        }
        LOG.info("despeckling {} page(s) with {} thread(s)", files.size(), config.jobs());

        @Nullable Path reportDir = config.reportDir();
        boolean reporting = reportDir != null;
        Reporter report =
                reportDir != null
                        ? reporterFactory.create(reportDir, config.flipbook())
                        : Reporter.noOp();
        // The report is the only consumer of per-page component counts, and counting is a full
        // connected-component labeling twice per page — skip it when no report will be written.
        ProcessOptions options =
                reporting ? config.options() : config.options().withoutComponentStats();

        List<Callable<PageOutcome>> tasks = new ArrayList<>(files.size());
        for (Path src : files) {
            tasks.add(() -> processOne(src, config, options, report));
        }
        // Platform workers: each page is CPU-bound Leptonica work (FFM downcalls pin virtual
        // threads' carriers). The fan-out fails fast and quiesces before throwing, so a failed
        // run never leaves workers writing into the output directory. Progress and the human
        // log arrive on this thread, ordered.
        List<PageOutcome> outcomes =
                Tasks.awaitAll(
                        Tasks.Workers.platform(config.jobs()),
                        tasks,
                        "despeckle",
                        (done, total) -> {
                            progress.onPage(done, total);
                            if (done % PROGRESS_EVERY == 0 || done == total) {
                                LOG.info("{}/{}", done, total);
                            }
                        });

        report.finish();

        long totalRemoved = 0;
        long blackRemoved = 0;
        int warnings = 0;
        for (PageOutcome outcome : outcomes) {
            totalRemoved += outcome.result().componentsRemoved();
            blackRemoved += outcome.result().blackPixelsRemoved();
            if (outcome.result().isOverRemoval()) {
                warnings++;
                LOG.warn(
                        "possible over-removal on {}: {}% of black pixels removed",
                        outcome.source(),
                        Math.round(outcome.result().removedBlackPixelRatio() * 100));
            }
        }
        if (reporting) {
            LOG.info(
                    "done: {} page(s), {} component(s) removed, {} over-removal warning(s)",
                    files.size(),
                    totalRemoved,
                    warnings);
        } else {
            // Without a report nothing counted components (the counting passes are skipped for
            // speed), so the summary speaks in the always-measured black-pixel terms.
            LOG.info(
                    "done: {} page(s), {} black pixel(s) removed, {} over-removal warning(s)",
                    files.size(),
                    blackRemoved,
                    warnings);
        }
        return new Summary(files.size(), totalRemoved, warnings);
    }

    private record PageOutcome(Path source, ProcessResult result) {}

    private PageOutcome processOne(Path src, Config config, ProcessOptions options, Reporter report)
            throws IOException {
        Path dest =
                CorpusFiles.mirrorDestination(
                        src, config.inputDir(), config.outputDir(), config.format().extension());
        try {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ProcessResult result = pageCleaner.clean(src, dest, config.format(), options);
            Path stem = config.inputDir().relativize(src);
            report.addPage(stem, src, dest, result);
            return new PageOutcome(src, result);
        } catch (IOException e) {
            // Re-throw with the page named: Tasks surfaces a task's IOException unchanged, so
            // this context reaches the user instead of being lost in a generic wrapper.
            throw new IOException("failed to process " + src, e);
        }
    }
}
