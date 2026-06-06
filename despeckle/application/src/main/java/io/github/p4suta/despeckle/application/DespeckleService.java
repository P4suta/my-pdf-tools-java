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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * Create a despeckle service over the injected adapters.
     *
     * @param pageCleaner the per-page despeckle port
     * @param reporterFactory the factory for the per-run report sink
     */
    public DespeckleService(PageCleaner pageCleaner, ReporterFactory reporterFactory) {
        this.pageCleaner = pageCleaner;
        this.reporterFactory = reporterFactory;
    }

    /**
     * Configuration for one despeckle run.
     *
     * @param inputDir directory of source pages (walked recursively)
     * @param outputDir directory to mirror cleaned pages into
     * @param format output format
     * @param glob file-name glob for input selection
     * @param jobs worker thread count
     * @param force whether to overwrite a non-empty output directory
     * @param options despeckle knobs
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
     * @param componentsRemoved total components removed across all pages
     * @param overRemovalWarnings number of pages flagged for possible over-removal
     */
    public record Summary(int pages, long componentsRemoved, int overRemovalWarnings) {}

    /**
     * Execute a run, reporting no per-page progress.
     *
     * @param config run configuration
     * @return the aggregate summary
     * @throws IOException on filesystem failure
     */
    public Summary run(Config config) throws IOException {
        return run(config, PageProgressListener.NO_OP);
    }

    /**
     * Execute a run, reporting each finished page to {@code progress}.
     *
     * @param config run configuration
     * @param progress called once per page as it completes (1-based count, total page count); may
     *     be invoked from the worker threads, so it must be thread-safe
     * @return the aggregate summary
     * @throws IOException on filesystem failure
     */
    public Summary run(Config config, PageProgressListener progress) throws IOException {
        OutputDirs.prepare(config.outputDir(), config.force());

        List<Path> files = CorpusFiles.collect(config.inputDir(), config.glob());
        if (files.isEmpty()) {
            LOG.warn("no images matched {} under {}", config.glob(), config.inputDir());
            return new Summary(0, 0, 0);
        }
        LOG.info("despeckling {} page(s) with {} thread(s)", files.size(), config.jobs());

        Reporter report =
                config.reportDir() == null
                        ? Reporter.noOp()
                        : reporterFactory.create(config.reportDir(), config.flipbook());

        AtomicInteger done = new AtomicInteger();
        List<PageOutcome> outcomes;
        ExecutorService pool = Executors.newFixedThreadPool(config.jobs());
        try {
            List<Callable<PageOutcome>> tasks = new ArrayList<>(files.size());
            for (Path src : files) {
                tasks.add(
                        () -> {
                            PageOutcome outcome = processOne(src, config, report);
                            int n = done.incrementAndGet();
                            progress.onPage(n, files.size());
                            if (n % PROGRESS_EVERY == 0 || n == files.size()) {
                                LOG.info("{}/{}", n, files.size());
                            }
                            return outcome;
                        });
            }
            outcomes = invokeAll(pool, tasks);
        } finally {
            pool.shutdown();
        }

        report.finish();

        long totalRemoved = 0;
        int warnings = 0;
        for (PageOutcome outcome : outcomes) {
            totalRemoved += outcome.result().componentsRemoved();
            if (outcome.result().isOverRemoval()) {
                warnings++;
                LOG.warn(
                        "possible over-removal on {}: {}% of black pixels removed",
                        outcome.source(),
                        Math.round(outcome.result().removedBlackPixelRatio() * 100));
            }
        }
        LOG.info(
                "done: {} page(s), {} component(s) removed, {} over-removal warning(s)",
                files.size(),
                totalRemoved,
                warnings);
        return new Summary(files.size(), totalRemoved, warnings);
    }

    private record PageOutcome(Path source, ProcessResult result) {}

    private PageOutcome processOne(Path src, Config config, Reporter report) {
        Path dest =
                CorpusFiles.mirrorDestination(
                        src, config.inputDir(), config.outputDir(), config.format().extension());
        try {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ProcessResult result = pageCleaner.clean(src, dest, config.format(), config.options());
            Path stem = config.inputDir().relativize(src);
            report.addPage(stem, src, dest, result);
            return new PageOutcome(src, result);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to process " + src, e);
        }
    }

    private static List<PageOutcome> invokeAll(
            ExecutorService pool, List<Callable<PageOutcome>> tasks) throws IOException {
        List<Future<PageOutcome>> futures;
        try {
            futures = pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("despeckle run interrupted", e);
        }
        List<PageOutcome> outcomes = new ArrayList<>(futures.size());
        for (Future<PageOutcome> future : futures) {
            try {
                outcomes.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("despeckle run interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("page processing failed", e.getCause());
            }
        }
        return outcomes;
    }
}
