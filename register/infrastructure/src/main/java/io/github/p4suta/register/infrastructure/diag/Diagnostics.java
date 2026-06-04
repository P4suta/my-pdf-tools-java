package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.domain.exception.RegisterErrorKind;
import io.github.p4suta.register.domain.exception.RegisterException;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.RunInfo;
import io.github.p4suta.register.port.Reporter;
import io.github.p4suta.shared.imaging.Pix;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in {@link Reporter} for a {@code --diag} run. Per page (called from the parallel render pass)
 * it re-reads the deskewed page from disk, draws the overlay image and records the page's {@link
 * PageDiagnostic}; at {@link #finish} it writes the JSONL log, the summary, the corpus before/after
 * overlay, the residual chart and an optional WebP flip-book. Thread-safe: each page writes its own
 * overlay file and appends to a synchronized list.
 *
 * <p>The deskewed page arrives as a path (the scratch the render pass read back), not a live {@code
 * Pix}, so the Leptonica image stack stays on this side of the {@code :port} boundary. The band the
 * column profile is clipped to is reconstructed from the diagnostic's recorded column.
 */
final class Diagnostics implements Reporter {

    private final Path dir;
    private final boolean flipbook;
    private final List<PageDiagnostic> pages = Collections.synchronizedList(new ArrayList<>());

    /**
     * Create a diagnostics collector writing into {@code dir} (created if absent).
     *
     * @param dir the output directory for overlays, the JSONL log, the summary and the corpus
     *     before/after overlay and residual chart
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
     * @param diagnostic the page's recorded state
     * @param deskewedPage the deskewed page on disk (the render pass's scratch)
     * @throws IOException if the page cannot be read back or the overlay cannot be written
     */
    @Override
    public void addPage(PageDiagnostic diagnostic, Path deskewedPage) throws IOException {
        try (Pix work = Pix.read(deskewedPage)) {
            BufferedImage page = toRgbImage(work);
            int[] rowInk = work.inkByRow();
            int[] columnInk = columnInk(work, diagnostic.column());
            BufferedImage overlay = DiagnosticRenderer.render(page, diagnostic, rowInk, columnInk);
            // Prefix with the page index so overlays stay unique (and collision-free under the
            // parallel render pass) even when source base names repeat across input subdirectories.
            Path out =
                    dir.resolve(
                            String.format(
                                    Locale.ROOT,
                                    "%04d-%s.diag.png",
                                    diagnostic.index(),
                                    baseName(diagnostic.source())));
            if (!ImageIO.write(overlay, "png", out.toFile())) {
                throw RegisterException.withDetail(
                        RegisterErrorKind.NATIVE_TOOL_FAILED,
                        "no PNG writer available for " + out,
                        null);
            }
        }
        pages.add(diagnostic);
    }

    /**
     * Write the end-of-run artifacts: the JSONL log, the summary, the corpus before/after overlay
     * and the residual chart — and, when enabled, the animated WebP flip-book of the registered
     * pages.
     *
     * @param info the run settings and references
     * @param outputs the registered output image paths in reading order (frames for the flip-book)
     * @throws IOException on write failure
     */
    @Override
    public void finish(RunInfo info, List<Path> outputs) throws IOException {
        List<PageDiagnostic> sorted = sortedPages();
        DiagnosticReport.writeJsonl(dir.resolve("pages.jsonl"), sorted);
        DiagnosticReport.writeSummary(dir.resolve("summary.txt"), info, sorted);
        writePng(CorpusOverlayRenderer.render(sorted), dir.resolve("corpus-overlay.png"));
        writePng(ResidualChartRenderer.render(sorted), dir.resolve("residuals.png"));
        if (flipbook) {
            WebpFlipbook.write(dir, outputs);
        }
    }

    private static void writePng(BufferedImage img, Path out) throws IOException {
        if (!ImageIO.write(img, "png", out.toFile())) {
            throw RegisterException.withDetail(
                    RegisterErrorKind.NATIVE_TOOL_FAILED,
                    "no PNG writer available for " + out,
                    null);
        }
    }

    private List<PageDiagnostic> sortedPages() {
        synchronized (pages) {
            List<PageDiagnostic> out = new ArrayList<>(pages);
            out.sort(Comparator.comparingInt(PageDiagnostic::index));
            return out;
        }
    }

    private BufferedImage toRgbImage(Pix work) throws IOException {
        Path tmp = Files.createTempFile(dir, ".work-", ".png");
        try {
            work.writePng(tmp);
            BufferedImage raw = ImageIO.read(tmp.toFile());
            if (raw == null) {
                throw RegisterException.withDetail(
                        RegisterErrorKind.IMAGE_UNREADABLE,
                        "ImageIO could not read back " + tmp,
                        null);
            }
            BufferedImage rgb =
                    new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.drawImage(raw, 0, 0, null);
            } finally {
                g.dispose();
            }
            return rgb;
        } finally {
            Files.deleteIfExists(tmp);
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
