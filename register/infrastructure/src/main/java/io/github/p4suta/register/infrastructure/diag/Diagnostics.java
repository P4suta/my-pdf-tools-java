package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.RunInfo;
import io.github.p4suta.register.infrastructure.process.TempDirs;
import io.github.p4suta.register.port.Reporter;
import io.github.p4suta.shared.imaging.ImageEncoder;
import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.process.WebpAnimation;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in {@link Reporter} for a {@code --diag} run. Per page (called from the parallel render pass)
 * it re-reads the deskewed page from disk, draws the overlay image and records the page's {@link
 * PageDiagnostic}; at {@link #finish} it writes the JSONL log, the summary, the corpus before/after
 * overlay, the residual chart and an optional WebP flip-book. Thread-safe: each page writes its own
 * overlay file and appends to a synchronized list.
 *
 * <p>Every still image is lossless WebP, written through the shared imaging island ({@link
 * ImageEncoder} for the AWT overlays/charts); the animated flip-book goes through the shared {@link
 * WebpAnimation}. The deskewed page arrives as a path (the scratch the render pass read back), not
 * a live {@code Pix}, so the Leptonica image stack stays on this side of the {@code :port}
 * boundary. The band the column profile is clipped to is reconstructed from the diagnostic's
 * recorded column.
 */
final class Diagnostics implements Reporter {

    /**
     * Flip-book frames taller than this are downscaled; the flip-book is a preview, not archival.
     */
    private static final int MAX_FRAME_HEIGHT = 1000;

    private final Path dir;
    private final boolean flipbook;
    private final List<PageDiagnostic> pages = Collections.synchronizedList(new ArrayList<>());

    /**
     * Create a diagnostics collector writing into {@code dir} (created if absent).
     *
     * @param flipbook whether to also assemble the registered pages into an animated WebP flip-book
     *     (needs libwebp's {@code img2webp})
     * @throws IOException if the directory cannot be created
     */
    Diagnostics(Path dir, boolean flipbook) throws IOException {
        Files.createDirectories(dir);
        this.dir = dir;
        this.flipbook = flipbook;
    }

    /**
     * Record one rendered page: re-read the deskewed page, draw its overlay and remember its
     * diagnostic.
     *
     * @throws IOException if the page cannot be read back or the overlay cannot be written
     */
    @Override
    public void addPage(PageDiagnostic diagnostic, Path deskewedPage) throws IOException {
        try (Pix work = Pix.read(deskewedPage)) {
            BufferedImage page = ImageEncoder.toBufferedImage(work);
            int[] rowInk = work.inkByRow();
            int[] columnInk = columnInk(work, diagnostic.column());
            BufferedImage overlay = DiagnosticRenderer.render(page, diagnostic, rowInk, columnInk);
            // Prefix with the page index so overlays stay unique (and collision-free under the
            // parallel render pass) even when source base names repeat across input subdirectories.
            Path out =
                    dir.resolve(
                            String.format(
                                    Locale.ROOT,
                                    "%04d-%s.diag.webp",
                                    diagnostic.index(),
                                    baseName(diagnostic.source())));
            ImageEncoder.writeWebp(overlay, out);
        }
        pages.add(diagnostic);
    }

    /**
     * Write the end-of-run artifacts: the JSONL log, the summary, the corpus before/after overlay
     * and the residual chart — and, when enabled, the animated WebP flip-book of the registered
     * pages.
     *
     * @param outputs the registered output image paths in reading order (frames for the flip-book)
     * @throws IOException on write failure
     */
    @Override
    public void finish(RunInfo info, List<Path> outputs) throws IOException {
        List<PageDiagnostic> sorted = sortedPages();
        DiagnosticReport.writeJsonl(dir.resolve("pages.jsonl"), sorted);
        DiagnosticReport.writeSummary(dir.resolve("summary.txt"), info, sorted);
        ImageEncoder.writeWebp(
                CorpusOverlayRenderer.render(sorted), dir.resolve("corpus-overlay.webp"));
        ImageEncoder.writeWebp(ResidualChartRenderer.render(sorted), dir.resolve("residuals.webp"));
        if (flipbook) {
            writeFlipbook(outputs);
        }
    }

    /**
     * Assemble {@code outputs} (the registered pages, in reading order) into {@code
     * dir/flipbook.webp} via the shared {@link WebpAnimation}. The frames are sampled down and each
     * is downscaled to at most {@link #MAX_FRAME_HEIGHT} px tall through a temporary PNG (img2webp
     * reads from files), so a long book stays a small preview. The frame scratch directory is
     * always removed.
     *
     * @throws IOException if frame extraction or filesystem work fails
     */
    private void writeFlipbook(List<Path> outputs) throws IOException {
        if (outputs.isEmpty()) {
            return;
        }
        List<Path> sampled = WebpAnimation.sampleFrames(outputs, WebpAnimation.DEFAULT_MAX_FRAMES);
        Path framesDir = Files.createTempDirectory(dir, ".flipbook-frames-");
        try {
            List<Path> frames = new ArrayList<>(sampled.size());
            int n = 0;
            for (Path output : sampled) {
                Path frame = framesDir.resolve(String.format(Locale.ROOT, "frame%05d.png", n++));
                try (Pix page = Pix.read(output)) {
                    if (page.height() > MAX_FRAME_HEIGHT) {
                        try (Pix small = page.scaleToHeight(MAX_FRAME_HEIGHT)) {
                            small.writePng(frame);
                        }
                    } else {
                        page.writePng(frame);
                    }
                }
                frames.add(frame);
            }
            WebpAnimation.assemble(
                    frames,
                    dir.resolve("flipbook.webp"),
                    WebpAnimation.DEFAULT_DELAY_MS,
                    WebpAnimation.DEFAULT_MAX_FRAMES,
                    "register.img2webp.path");
        } finally {
            TempDirs.deleteRecursively(framesDir);
        }
    }

    private List<PageDiagnostic> sortedPages() {
        synchronized (pages) {
            List<PageDiagnostic> out = new ArrayList<>(pages);
            out.sort(Comparator.comparingInt(PageDiagnostic::index));
            return out;
        }
    }

    private static int[] columnInk(Pix work, PageDiagnostic.@Nullable Column column) {
        if (column == null) {
            return work.inkByColumn();
        }
        try (Pix clip =
                work.clip(
                        0,
                        column.bandStart(),
                        work.width(),
                        column.bandEnd() - column.bandStart())) {
            return clip.inkByColumn();
        }
    }

    private static String baseName(String source) {
        int dot = source.lastIndexOf('.');
        return dot < 0 ? source : source.substring(0, dot);
    }
}
