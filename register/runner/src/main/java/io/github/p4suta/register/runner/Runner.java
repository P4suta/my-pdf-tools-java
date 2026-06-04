package io.github.p4suta.register.runner;

import io.github.p4suta.register.core.Canvas;
import io.github.p4suta.register.core.OutputFormat;
import io.github.p4suta.register.core.PageObservation;
import io.github.p4suta.register.core.PageRenderer;
import io.github.p4suta.register.core.PaperSize;
import io.github.p4suta.register.core.Parity;
import io.github.p4suta.register.core.Pix;
import io.github.p4suta.register.core.Reference;
import io.github.p4suta.register.core.RegisterOptions;
import io.github.p4suta.register.diag.Diagnostics;
import io.github.p4suta.register.diag.RunInfo;
import io.github.p4suta.register.util.Tasks;
import io.github.p4suta.register.util.TempDirs;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * each deskewed page back and places it against that reference. Only the small per-page detections
 * are held between passes (the deskewed pages live on scratch), so deskew and detection run once
 * and memory stays flat.
 *
 * <p>This is the only place that touches the filesystem and threads, keeping {@code core} a pure
 * pipeline that a future GUI could reuse unchanged.
 */
public final class Runner {

    private static final Logger LOG = LoggerFactory.getLogger(Runner.class);

    private static final int PROGRESS_EVERY = 25;

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
        prepareOutputDir(requested.outputDir(), requested.force());

        List<Path> files = collectFiles(requested.inputDir(), requested.glob());
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
        @Nullable Diagnostics diagnostics =
                diagDir == null ? null : new Diagnostics(diagDir, config.flipbook());
        PageRenderer renderer = new PageRenderer(config.format(), config.options(), diagnostics);

        // The analysis pass writes each deskewed page here; the render pass reads it back, so the
        // costly deskew and detection run once per page, not again in pass two. Removed at the end.
        Path scratchDir = TempDirs.createBeside(config.outputDir(), ".register-deskewed-");
        ExecutorService pool = Executors.newFixedThreadPool(config.jobs());
        try {
            // ----- Pass 1: deskew once, detect, cache the deskewed page for pass two -----
            List<AnalyzedPage> pages = analyzePass(files, scratchDir, renderer, pool);
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
            List<Path> outputs = renderPass(pages, renderer, reference, canvas, config, pool);

            if (diagnostics != null) {
                writeDiagnostics(
                        diagnostics,
                        config,
                        canvas,
                        paper,
                        reference,
                        files.size(),
                        analyzed,
                        outputs);
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
            TempDirs.deleteRecursively(scratchDir);
        }
    }

    /**
     * Pass 1: deskew and detect every page in parallel, caching each deskewed page on scratch for
     * pass two to read back.
     */
    private static List<AnalyzedPage> analyzePass(
            List<Path> files, Path scratchDir, PageRenderer renderer, ExecutorService pool)
            throws IOException {
        List<Callable<AnalyzedPage>> tasks = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            int index = i;
            Path src = files.get(i);
            Path scratch = scratchDir.resolve(String.format(Locale.ROOT, "%06d.tif", index));
            tasks.add(() -> analyzeOne(src, index, scratch, renderer));
        }
        return Tasks.awaitAll(pool, tasks, "register run interrupted", "page processing failed");
    }

    /** The detected main-column boxes from pass 1, one per page that had a detectable column. */
    private static List<PageObservation> toObservations(List<AnalyzedPage> pages) {
        List<PageObservation> observations = new ArrayList<>();
        for (AnalyzedPage page : pages) {
            page.analysis()
                    .detection()
                    .ifPresent(
                            det ->
                                    observations.add(
                                            new PageObservation(
                                                    page.index(), page.parity(), det.column())));
        }
        return observations;
    }

    /**
     * Pass 2: place every (already deskewed) page against the reference in parallel, logging
     * progress every {@link #PROGRESS_EVERY} pages.
     */
    private static List<Path> renderPass(
            List<AnalyzedPage> pages,
            PageRenderer renderer,
            @Nullable Reference reference,
            Canvas canvas,
            Config config,
            ExecutorService pool)
            throws IOException {
        AtomicInteger done = new AtomicInteger();
        int total = pages.size();
        List<Callable<Path>> tasks = new ArrayList<>(total);
        for (AnalyzedPage page : pages) {
            tasks.add(
                    () -> {
                        Path dest = renderOne(page, renderer, reference, canvas, config);
                        int n = done.incrementAndGet();
                        if (n % PROGRESS_EVERY == 0 || n == total) {
                            LOG.info("{}/{}", n, total);
                        }
                        return dest;
                    });
        }
        return Tasks.awaitAll(pool, tasks, "register run interrupted", "page processing failed");
    }

    /**
     * The canvas paper: the explicit {@code --paper} when given, otherwise auto-detected from the
     * median scanned page size at the run dpi (see {@link PaperSize#fromScan}). {@code scans} is
     * the whole corpus, so the median ignores the odd badly scanned or foldout page.
     */
    private static PaperSize resolvePaper(
            @Nullable PaperSize requested, List<AnalyzedPage> pages, int dpi) {
        if (requested != null) {
            return requested;
        }
        int[] widths = pages.stream().mapToInt(p -> p.analysis().width()).sorted().toArray();
        int[] heights = pages.stream().mapToInt(p -> p.analysis().height()).sorted().toArray();
        double widthMm = widths[widths.length / 2] * 25.4 / dpi;
        double heightMm = heights[heights.length / 2] * 25.4 / dpi;
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
     * (which {@code stamp-dpi.py} writes onto the extracted pages) so the output is reproduced at
     * the source's native resolution. An explicit {@code --dpi} always wins; inputs that carry no
     * resolution (raw PBM) fall back to {@link RegisterOptions#DEFAULT_DPI}.
     */
    private static Config inheritScanDpi(Config config, List<Path> files) {
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
    private static int firstScanResolution(List<Path> files) {
        for (Path file : files) {
            try (Pix page = Pix.read(file)) {
                int resolution = page.resolution();
                if (resolution > 0) {
                    return resolution;
                }
            }
        }
        return 0;
    }

    /** A pass-1 result: the page's source, its deskewed-image scratch file, and its analysis. */
    private record AnalyzedPage(
            int index, Parity parity, Path src, Path scratch, PageRenderer.Analysis analysis) {}

    private static AnalyzedPage analyzeOne(
            Path src, int index, Path scratch, PageRenderer renderer) {
        try (Pix page = Pix.read(src)) {
            PageRenderer.Analysis analysis = renderer.analyze(page, scratch);
            return new AnalyzedPage(index, Parity.of(index), src, scratch, analysis);
        }
    }

    private static Path renderOne(
            AnalyzedPage page,
            PageRenderer renderer,
            @Nullable Reference reference,
            Canvas canvas,
            Config config)
            throws IOException {
        Path dest =
                mirrorDestination(
                        page.src(), config.inputDir(), config.outputDir(), config.format());
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        PageRenderer.Analysis analysis = page.analysis();
        try (Pix deskewed = Pix.read(page.scratch())) {
            renderer.renderPlaced(
                    deskewed,
                    analysis,
                    page.index(),
                    page.parity(),
                    fileName(page.src()),
                    reference,
                    canvas,
                    dest);
        }
        return dest;
    }

    private static void writeDiagnostics(
            Diagnostics diagnostics,
            Config config,
            Canvas canvas,
            PaperSize paper,
            @Nullable Reference reference,
            int total,
            int analyzed,
            List<Path> outputs)
            throws IOException {
        RunInfo info =
                new RunInfo(
                        paper.displayName(),
                        config.options().canvasDpi(),
                        canvas.width(),
                        canvas.height(),
                        config.options().deskew(),
                        config.options().anchor().name(),
                        config.options().outlierRatio(),
                        total,
                        analyzed,
                        reference == null ? null : reference.recto(),
                        reference == null ? null : reference.verso());
        diagnostics.finish(info, outputs);
        LOG.info("diagnostics written to {}", config.diagDir());
    }

    private static String fileName(Path p) {
        Path name = p.getFileName();
        return name == null ? p.toString() : name.toString();
    }

    /**
     * Prepare the output directory: create it if absent, or reject a non-empty one unless {@code
     * force}. Package-private for unit testing.
     */
    static void prepareOutputDir(Path dir, boolean force) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> entries = Files.list(dir)) {
                if (entries.findAny().isPresent() && !force) {
                    throw new IOException(
                            "output directory " + dir + " is not empty; pass --force to overwrite");
                }
            }
        } else {
            Files.createDirectories(dir);
        }
    }

    /**
     * Every regular file under {@code root} whose name matches {@code glob}, in path order.
     * Package-private for unit testing.
     */
    static List<Path> collectFiles(Path root, String glob) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(
                            p -> {
                                Path name = p.getFileName();
                                return name != null && matcher.matches(name);
                            })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /**
     * The output path for {@code src}: its {@code inputDir}-relative path resolved under {@code
     * outputDir}, with the extension swapped to {@code format}'s (or kept when the format carries
     * none, i.e. {@code --format same}). Package-private for unit testing.
     */
    static Path mirrorDestination(Path src, Path inputDir, Path outputDir, OutputFormat format) {
        Path relative = inputDir.relativize(src);
        Path dest = outputDir.resolve(relative);
        String extension = format.extension();
        Path name = dest.getFileName();
        if (extension == null || name == null) {
            return dest;
        }
        return dest.resolveSibling(stripExtension(name.toString()) + "." + extension);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
