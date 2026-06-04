package io.github.p4suta.register.application;

import io.github.p4suta.register.domain.model.Canvas;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PageAnalysis;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.PageObservation;
import io.github.p4suta.register.domain.model.PaperSize;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.domain.model.RunInfo;
import io.github.p4suta.register.domain.service.Reference;
import io.github.p4suta.register.port.PageRegistrar;
import io.github.p4suta.register.port.Reporter;
import io.github.p4suta.register.port.ReporterFactory;
import io.github.p4suta.shared.io.CorpusFiles;
import io.github.p4suta.shared.io.OutputDirs;
import io.github.p4suta.shared.kernel.Medians;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks an input directory and registers every matching page onto a fixed paper canvas across a
 * fixed thread pool, mirroring the directory layout into the output directory.
 *
 * <p>Unlike a per-page filter, registration is corpus-aware: it runs in two passes. Pass one
 * deskews and analyzes every page to find its main-text-column box, writing the deskewed page to a
 * scratch file; those boxes are reduced to a per-parity median {@link Reference}; pass two reads
 * each deskewed page back and places it against that reference. Only the small per-page analyses
 * are held between passes (the deskewed pages live on scratch), so deskew and detection run once
 * and memory stays flat.
 *
 * <p>The only place that touches the filesystem and threads — the registrar and the reporter
 * factory are injected ports, so this service depends only on {@code :port} and {@code :domain},
 * keeping registration a pure operation a future GUI could reuse unchanged.
 */
public final class RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

    private static final int PROGRESS_EVERY = 25;

    private final PageRegistrar pageRegistrar;
    private final ReporterFactory reporterFactory;

    /**
     * Create a registration service over the injected adapters.
     *
     * @param pageRegistrar the per-page registration port
     * @param reporterFactory the factory for the per-run diagnostics reporter
     */
    public RegistrationService(PageRegistrar pageRegistrar, ReporterFactory reporterFactory) {
        this.pageRegistrar = pageRegistrar;
        this.reporterFactory = reporterFactory;
    }

    /**
     * Configuration for one registration run.
     *
     * @param inputDir directory of source pages (walked recursively)
     * @param outputDir directory to mirror registered pages into
     * @param format output format
     * @param glob file-name glob for input selection
     * @param jobs worker thread count
     * @param force whether to overwrite a non-empty output directory
     * @param options registration knobs
     * @param diagDir diagnostics output directory, or null to disable diagnostics
     * @param flipbook whether diagnostics also assemble an animated WebP flip-book (needs {@code
     *     --diag} and libwebp's {@code img2webp})
     */
    public record Config(
            Path inputDir,
            Path outputDir,
            OutputFormat format,
            String glob,
            int jobs,
            boolean force,
            RegisterOptions options,
            @Nullable Path diagDir,
            boolean flipbook) {}

    /**
     * Aggregate outcome of a run.
     *
     * @param pages number of pages rendered
     * @param analyzed number of pages whose main column was detected (the rest were centered)
     */
    public record Summary(int pages, int analyzed) {}

    /**
     * Execute a run.
     *
     * @param requested run configuration as given on the command line (its empty {@code --dpi} is
     *     resolved from the inputs before rendering)
     * @return the aggregate summary
     * @throws IOException on filesystem failure
     */
    public Summary run(Config requested) throws IOException {
        OutputDirs.prepare(requested.outputDir(), requested.force());

        List<Path> files = CorpusFiles.collect(requested.inputDir(), requested.glob());
        if (files.isEmpty()) {
            LOG.warn("no images matched {} under {}", requested.glob(), requested.inputDir());
            return new Summary(0, 0);
        }
        // With no explicit --dpi, the canvas inherits the scan's own resolution, so a 600- or
        // 1200-dpi book is reproduced at its native resolution instead of being resampled to a
        // fixed default.
        Config config = inheritScanDpi(requested, files);
        LOG.info("registering {} page(s) with {} thread(s)", files.size(), config.jobs());

        Path diagDir = config.diagDir();
        boolean recordDiagnostics = diagDir != null;
        Reporter reporter =
                diagDir != null
                        ? reporterFactory.create(diagDir, config.flipbook())
                        : Reporter.noOp();

        // The analysis pass writes each deskewed page here; the render pass reads it back, so the
        // costly deskew and detection run once per page, not again in pass two. Removed at the end.
        Path scratchDir = createBeside(config.outputDir(), ".register-deskewed-");
        ExecutorService pool = Executors.newFixedThreadPool(config.jobs());
        try {
            // ----- Pass 1: deskew once, detect, cache the deskewed page for pass two -----
            List<AnalyzedPage> pages =
                    analyzePass(files, scratchDir, config, recordDiagnostics, pool);
            List<PageObservation> observations = toObservations(pages);
            int analyzed = observations.size();

            // The canvas is the chosen paper at the run dpi. With no --paper it is auto-detected
            // from the median scanned page size, so a book lands on its own size without the
            // operator naming it. Resolved here, after pass 1 has measured every page.
            PaperSize paper =
                    resolvePaper(config.options().paper(), pages, config.options().canvasDpi());
            Canvas canvas = Canvas.of(paper, config.options().canvasDpi());

            // ----- Reduce: per-parity reference layout -----
            Reference reference =
                    observations.isEmpty()
                            ? null
                            : Reference.fromObservations(
                                    observations, config.options().outlierRatio());
            if (reference == null) {
                LOG.warn("no main column detected on any page; every page is centered");
            } else {
                LOG.info(
                        "reference layout derived from {} of {} page(s)",
                        observations.size(),
                        files.size());
            }

            // ----- Pass 2: place every (already deskewed) page against the reference -----
            List<Path> outputs =
                    renderPass(pages, reference, canvas, config, recordDiagnostics, reporter, pool);

            if (recordDiagnostics) {
                RunInfo info =
                        new RunInfo(
                                paper.displayName(),
                                config.options().canvasDpi(),
                                canvas.width(),
                                canvas.height(),
                                config.options().deskew(),
                                config.options().anchor().name(),
                                config.options().outlierRatio(),
                                files.size(),
                                analyzed,
                                reference == null ? null : reference.recto(),
                                reference == null ? null : reference.verso());
                reporter.finish(info, outputs);
                LOG.info("diagnostics written to {}", config.diagDir());
            }

            LOG.info(
                    "done: {} page(s) onto {}x{} px ({}) canvas, {} analyzed",
                    files.size(),
                    canvas.width(),
                    canvas.height(),
                    paper.displayName(),
                    analyzed);
            return new Summary(files.size(), analyzed);
        } finally {
            pool.shutdown();
            deleteRecursively(scratchDir);
        }
    }

    /**
     * Pass 1: deskew and detect every page in parallel, caching each deskewed page on scratch for
     * pass two to read back.
     */
    private List<AnalyzedPage> analyzePass(
            List<Path> files,
            Path scratchDir,
            Config config,
            boolean recordDiagnostics,
            ExecutorService pool)
            throws IOException {
        List<Callable<AnalyzedPage>> tasks = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            int index = i;
            Path src = files.get(i);
            Path scratch = scratchDir.resolve(String.format(Locale.ROOT, "%06d.tif", index));
            tasks.add(
                    () -> {
                        PageAnalysis analysis =
                                pageRegistrar.analyze(
                                        src, scratch, config.options(), recordDiagnostics);
                        return new AnalyzedPage(index, Parity.of(index), src, scratch, analysis);
                    });
        }
        return awaitAll(pool, tasks);
    }

    /** The detected main-column boxes from pass 1, one per page that had a detectable column. */
    private static List<PageObservation> toObservations(List<AnalyzedPage> pages) {
        return pages.stream()
                .flatMap(
                        p ->
                                p.analysis().detection().stream()
                                        .map(
                                                det ->
                                                        new PageObservation(
                                                                p.index(),
                                                                p.parity(),
                                                                det.column())))
                .toList();
    }

    /**
     * Pass 2: place every (already deskewed) page against the reference in parallel, logging
     * progress every {@link #PROGRESS_EVERY} pages.
     */
    private List<Path> renderPass(
            List<AnalyzedPage> pages,
            @Nullable Reference reference,
            Canvas canvas,
            Config config,
            boolean recordDiagnostics,
            Reporter reporter,
            ExecutorService pool)
            throws IOException {
        AtomicInteger done = new AtomicInteger();
        int total = pages.size();
        List<Callable<Path>> tasks = new ArrayList<>(total);
        for (AnalyzedPage page : pages) {
            tasks.add(
                    () -> {
                        Path dest =
                                renderOne(
                                        page,
                                        reference,
                                        canvas,
                                        config,
                                        recordDiagnostics,
                                        reporter);
                        int n = done.incrementAndGet();
                        if (n % PROGRESS_EVERY == 0 || n == total) {
                            LOG.info("{}/{}", n, total);
                        }
                        return dest;
                    });
        }
        return awaitAll(pool, tasks);
    }

    /**
     * The canvas paper: the explicit {@code --paper} when given, otherwise auto-detected from the
     * median scanned page size at the run dpi (see {@link PaperSize#fromScan}). {@code pages} is
     * the whole corpus, so the median ignores the odd badly scanned or foldout page.
     */
    private static PaperSize resolvePaper(
            @Nullable PaperSize requested, List<AnalyzedPage> pages, int dpi) {
        if (requested != null) {
            return requested;
        }
        int[] widths = pages.stream().mapToInt(p -> p.analysis().width()).toArray();
        int[] heights = pages.stream().mapToInt(p -> p.analysis().height()).toArray();
        double widthMm = Medians.upperMedian(widths) * 25.4 / dpi;
        double heightMm = Medians.upperMedian(heights) * 25.4 / dpi;
        PaperSize paper = PaperSize.fromScan(widthMm, heightMm);
        LOG.info(
                "no --paper given; auto-detected {} from the median scan size {}x{} mm",
                paper.displayName(),
                Math.round(widthMm),
                Math.round(heightMm));
        return paper;
    }

    /**
     * Resolve the canvas resolution when none was given: inherit the inputs' own scan resolution
     * (so the output is reproduced at the source's native resolution). An explicit {@code --dpi}
     * always wins; inputs that carry no resolution (raw PBM) fall back to {@link
     * RegisterOptions#DEFAULT_DPI}.
     */
    private Config inheritScanDpi(Config config, List<Path> files) {
        RegisterOptions options = config.options();
        if (options.dpi().isPresent()) {
            return config;
        }
        int scanDpi = firstScanResolution(files);
        if (scanDpi <= 0) {
            LOG.info(
                    "inputs carry no resolution; canvas uses the default {} dpi",
                    RegisterOptions.DEFAULT_DPI);
            return config;
        }
        LOG.info("no --dpi given; inheriting the inputs' scan resolution of {} dpi", scanDpi);
        RegisterOptions inherited =
                new RegisterOptions(
                        OptionalInt.of(scanDpi),
                        options.paper(),
                        options.deskew(),
                        options.scale(),
                        options.outlierRatio(),
                        options.anchor());
        return new Config(
                config.inputDir(),
                config.outputDir(),
                config.format(),
                config.glob(),
                config.jobs(),
                config.force(),
                inherited,
                config.diagDir(),
                config.flipbook());
    }

    /** The resolution of the first input that carries one, or 0 if none do. */
    private int firstScanResolution(List<Path> files) {
        for (Path file : files) {
            int resolution = pageRegistrar.readScanResolution(file);
            if (resolution > 0) {
                return resolution;
            }
        }
        return 0;
    }

    /** A pass-1 result: the page's source, its deskewed-image scratch file, and its analysis. */
    private record AnalyzedPage(
            int index, Parity parity, Path src, Path scratch, PageAnalysis analysis) {}

    private Path renderOne(
            AnalyzedPage page,
            @Nullable Reference reference,
            Canvas canvas,
            Config config,
            boolean recordDiagnostics,
            Reporter reporter)
            throws IOException {
        Path dest =
                CorpusFiles.mirrorDestination(
                        page.src(),
                        config.inputDir(),
                        config.outputDir(),
                        config.format().extension());
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        PageDiagnostic diagnostic =
                pageRegistrar.renderPlaced(
                        page.scratch(),
                        page.analysis(),
                        page.index(),
                        page.parity(),
                        fileName(page.src()),
                        reference,
                        canvas,
                        dest,
                        config.format(),
                        config.options());
        if (recordDiagnostics) {
            reporter.addPage(diagnostic, page.scratch());
        }
        return dest;
    }

    /**
     * Run every task on {@code pool} in submission order, surfacing the first failure: a task's own
     * {@link IOException} is re-thrown unchanged, any other failure is wrapped, and an interruption
     * restores the interrupt flag.
     */
    private static <T> List<T> awaitAll(ExecutorService pool, List<Callable<T>> tasks)
            throws IOException {
        List<Future<T>> futures;
        try {
            futures = pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("register run interrupted", e);
        }
        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("register run interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                throw new IOException("page processing failed", cause);
            }
        }
        return results;
    }

    /**
     * Create a temporary directory beside {@code sibling} (under its parent), so the scratch space
     * shares a filesystem with the output it serves. Falls back to the working directory when
     * {@code sibling} has no usable parent.
     */
    private static Path createBeside(Path sibling, String prefix) throws IOException {
        Path parent = sibling.toAbsolutePath().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            return Files.createTempDirectory(parent, prefix);
        }
        return Files.createTempDirectory(prefix);
    }

    /**
     * Delete {@code dir} and everything under it, best-effort (cleanup must never mask a result).
     */
    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(RegistrationService::deleteQuietly);
        } catch (IOException e) {
            LOG.warn("could not clean up temp directory {}: {}", dir, e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("could not delete {}: {}", path, e.getMessage());
        }
    }

    private static String fileName(Path p) {
        Path name = p.getFileName();
        return name == null ? p.toString() : name.toString();
    }
}
