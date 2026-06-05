package io.github.p4suta.despeckle.infrastructure.report;

import io.github.p4suta.despeckle.domain.model.PageStat;
import io.github.p4suta.despeckle.domain.model.ProcessResult;
import io.github.p4suta.despeckle.port.Reporter;
import io.github.p4suta.shared.imaging.ImageEncoder;
import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.process.WebpAnimation;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;

/**
 * Optional before / overlay / after report, plus a corpus-wide diagnostic suite — the {@link
 * Reporter} adapter backed by an HTML page tree.
 *
 * <p>For each page it writes the original and cleaned images plus an overlay that paints every
 * removed pixel red over the original; at {@link #finish()} it rolls the per-page stats into three
 * corpus artifacts — a removed-pixel {@link RemovedHeatmap heatmap}, a {@link
 * ConvergenceChartRenderer component-convergence chart} and a {@link RemovalChartRenderer per-page
 * removal chart} — and, with {@code --flipbook}, an animated WebP {@link WebpAnimation flip-book}
 * of the overlays. Every still image is lossless WebP, written through the shared imaging island
 * ({@link Pix#writeWebp} for the page rasters, {@link ImageEncoder} for the AWT charts/overlays);
 * the animated flip-book goes through {@link WebpAnimation}. It emits an {@code index.html} tying
 * them together. This is the human eyeballing surface; it is read-only with respect to the
 * pipeline.
 *
 * <p>Both {@code inputImage} and {@code outputImage} are file paths on disk; the Leptonica {@link
 * Pix} and the AWT images never leave a method body, keeping the image stack confined to {@code
 * :infrastructure}.
 */
public final class HtmlReporter implements Reporter {

    private static final int RED = 0xFF0000;
    private static final int LUMA_MIDPOINT = 128;

    /** Every report image is lossless WebP. */
    private static final String PANEL_EXT = "webp";

    private final Path outDir;
    private final boolean flipbook;

    private final RemovedHeatmap heatmap = new RemovedHeatmap();
    private final ConcurrentLinkedQueue<PageStat> stats = new ConcurrentLinkedQueue<>();

    /**
     * Construct a reporter over an already-prepared report directory tree. Built through {@link
     * HtmlReporterFactory#create(Path, boolean)} so the {@code before}/{@code overlay}/{@code
     * after} directories are created exactly once.
     *
     * @param outDir the report root, with its panel sub-directories already created
     * @param flipbook whether to assemble the animated-WebP overlay flip-book at finish
     */
    HtmlReporter(Path outDir, boolean flipbook) {
        this.outDir = outDir;
        this.flipbook = flipbook;
    }

    /**
     * Render and record the three panels for one page, folding its removed pixels into the corpus
     * heatmap as it goes. Thread-safe.
     *
     * @param relativeStem page path relative to the input root
     * @param inputImage original page on disk
     * @param outputImage cleaned page on disk
     * @param result the per-page outcome
     * @throws IOException if a panel cannot be written
     */
    @Override
    public void addPage(Path relativeStem, Path inputImage, Path outputImage, ProcessResult result)
            throws IOException {
        String stem = stripExtension(relativeStem.toString());
        Path beforeWebp = panelPath("before", stem);
        Path afterWebp = panelPath("after", stem);
        Path overlayWebp = panelPath("overlay", stem);

        BufferedImage beforeImg;
        BufferedImage afterImg;
        try (Pix before = Pix.read(inputImage)) {
            before.writeWebp(beforeWebp);
            beforeImg = ImageEncoder.toBufferedImage(before);
        }
        try (Pix after = Pix.read(outputImage)) {
            after.writeWebp(afterWebp);
            afterImg = ImageEncoder.toBufferedImage(after);
        }
        writeOverlayAndAccumulate(beforeImg, afterImg, overlayWebp);

        stats.add(
                new PageStat(
                        stem,
                        result.componentsBefore(),
                        result.componentsAfter(),
                        result.removedBlackPixelRatio()));
    }

    /**
     * Write the corpus artifacts and {@code index.html} listing every page.
     *
     * @throws IOException if an artifact cannot be written
     */
    @Override
    public void finish() throws IOException {
        List<PageStat> sorted =
                stats.stream().sorted(Comparator.comparing(PageStat::stem)).toList();
        long totalRemoved = sorted.stream().mapToLong(PageStat::componentsRemoved).sum();

        String heatmapFile = writeArtifact("removed-heatmap", heatmap.render(sorted.size()));
        String convergenceFile =
                writeArtifact("corpus-convergence", ConvergenceChartRenderer.render(sorted));
        String removalFile = writeArtifact("removal-chart", RemovalChartRenderer.render(sorted));

        boolean flipbookWritten = flipbook && writeFlipbook(sorted);

        Files.writeString(
                outDir.resolve("index.html"),
                renderHtml(
                        sorted,
                        totalRemoved,
                        heatmapFile,
                        convergenceFile,
                        removalFile,
                        flipbookWritten ? "flipbook.webp" : null),
                StandardCharsets.UTF_8);
    }

    private boolean writeFlipbook(List<PageStat> sorted) throws IOException {
        List<Path> overlays = new ArrayList<>(sorted.size());
        for (PageStat stat : sorted) {
            overlays.add(outDir.resolve("overlay").resolve(stat.stem() + "." + PANEL_EXT));
        }
        return WebpAnimation.assemble(
                overlays,
                outDir.resolve("flipbook.webp"),
                WebpAnimation.DEFAULT_DELAY_MS,
                WebpAnimation.DEFAULT_MAX_FRAMES,
                "despeckle.img2webp.path");
    }

    /** Write a corpus chart as lossless WebP; returns the file name written, for the HTML link. */
    private String writeArtifact(String basename, BufferedImage img) throws IOException {
        ImageEncoder.writeWebp(img, outDir.resolve(basename + ".webp"));
        return basename + ".webp";
    }

    private Path panelPath(String panel, String stem) throws IOException {
        Path path = outDir.resolve(panel).resolve(stem + "." + PANEL_EXT);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return path;
    }

    /**
     * Build the red-over-grey overlay for one page from the already-decoded before/after rasters
     * and, in the same single pass, drop each removed pixel into the calling thread's heatmap
     * histogram. The overlay is written as lossless WebP via the shared imaging island.
     *
     * <p>Pixels are pulled a row at a time via the bulk {@code getRGB(x, y, w, 1, ...)} — one call
     * per row, not one per pixel — while the luma test (and so the set of "removed" pixels) is
     * byte-for-byte the same as the per-pixel form.
     */
    private void writeOverlayAndAccumulate(
            BufferedImage before, BufferedImage after, Path overlayWebp) throws IOException {
        int width = Math.min(before.getWidth(), after.getWidth());
        int height = Math.min(before.getHeight(), after.getHeight());
        BufferedImage overlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        long[] histogram = heatmap.threadHistogram();
        int[] beforeRow = new int[width];
        int[] afterRow = new int[width];
        int[] overlayRow = new int[width];
        for (int y = 0; y < height; y++) {
            before.getRGB(0, y, width, 1, beforeRow, 0, width);
            after.getRGB(0, y, width, 1, afterRow, 0, width);
            for (int x = 0; x < width; x++) {
                int beforeLuma = luma(beforeRow[x]);
                boolean removed = beforeLuma < LUMA_MIDPOINT && luma(afterRow[x]) >= LUMA_MIDPOINT;
                if (removed) {
                    overlayRow[x] = RED;
                    histogram[RemovedHeatmap.binIndex(x, y, width, height)]++;
                } else {
                    overlayRow[x] = gray(beforeLuma);
                }
            }
            overlay.setRGB(0, y, width, 1, overlayRow, 0, width);
        }
        ImageEncoder.writeWebp(overlay, overlayWebp);
    }

    private static int luma(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static int gray(int luma) {
        return (luma << 16) | (luma << 8) | luma;
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        int sep = path.lastIndexOf('/');
        return dot > sep ? path.substring(0, dot) : path;
    }

    private static String renderHtml(
            List<PageStat> rows,
            long totalRemoved,
            String heatmapFile,
            String convergenceFile,
            String removalFile,
            @Nullable String flipbookFile) {
        StringBuilder html = new StringBuilder(8192);
        html.append(
                        """
                        <!doctype html><html lang="ja"><head><meta charset="utf-8">\
                        <title>despeckle report</title><style>\
                        body{font-family:system-ui,sans-serif;margin:2rem;background:#111;color:#eee}\
                        h1{font-size:1.2rem}h2{font-size:1rem;color:#aaa;margin-top:1.5rem}\
                        table{border-collapse:collapse;width:100%}\
                        th,td{padding:.4rem .6rem;border-bottom:1px solid #333;vertical-align:top}\
                        th{text-align:left;font-weight:600;color:#aaa}\
                        .corpus{display:flex;flex-wrap:wrap;gap:1rem;align-items:flex-start;margin:.6rem 0 1.2rem}\
                        .corpus figure{margin:0}.corpus img{max-width:400px;height:auto;background:#fff;border:1px solid #333}\
                        .corpus figcaption{font-size:.8rem;color:#888;text-align:center;margin-top:.2rem}\
                        .panels{display:grid;grid-template-columns:repeat(3,1fr);gap:.4rem;margin-top:.4rem}\
                        .panels img{width:100%;height:auto;background:#fff}\
                        .panels figcaption{font-size:.75rem;color:#888;text-align:center}\
                        .stem{font-family:ui-monospace,monospace;font-size:.9rem}\
                        .warn{color:#f66}</style></head><body>\
                        """)
                .append("<h1>despeckle report &mdash; ")
                .append(rows.size())
                .append(" page(s), ")
                .append(totalRemoved)
                .append(" component(s) removed</h1>")
                .append("<h2>corpus</h2><div class=\"corpus\">")
                .append(corpusFigure(heatmapFile, "removed-pixel heatmap"))
                .append(corpusFigure(convergenceFile, "component convergence (before → after)"))
                .append(corpusFigure(removalFile, "per-page removal"));
        if (flipbookFile != null) {
            html.append(corpusFigure(flipbookFile, "overlay flip-book"));
        }
        html.append("</div><h2>pages</h2><table>")
                .append("<tr><th>page</th><th>removed</th><th>black&nbsp;cut</th>")
                .append("<th>before / overlay / after</th></tr>");
        for (PageStat row : rows) {
            String stem = escape(row.stem());
            int pct = row.removedPercent();
            boolean warn = row.removedRatio() > ProcessResult.OVER_REMOVAL_WARN_RATIO;
            html.append("<tr><td class=\"stem\">")
                    .append(stem)
                    .append("</td><td>")
                    .append(row.componentsRemoved())
                    .append("</td><td")
                    .append(warn ? " class=\"warn\"" : "")
                    .append('>')
                    .append(pct)
                    .append("%</td><td><div class=\"panels\">")
                    .append(figure("before", stem))
                    .append(figure("overlay", stem))
                    .append(figure("after", stem))
                    .append("</div></td></tr>");
        }
        return html.append("</table></body></html>").toString();
    }

    private static String corpusFigure(String file, String caption) {
        return "<figure><img src=\""
                + file
                + "\" loading=\"lazy\"><figcaption>"
                + caption
                + "</figcaption></figure>";
    }

    private static String figure(String panel, String stem) {
        return "<figure><img src=\""
                + panel
                + "/"
                + stem
                + "."
                + PANEL_EXT
                + "\" loading=\"lazy\"><figcaption>"
                + panel
                + "</figcaption></figure>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
