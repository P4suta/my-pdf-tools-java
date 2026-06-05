package io.github.p4suta.register.infrastructure.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.Detection;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.RunInfo;
import io.github.p4suta.register.domain.model.Skew;
import io.github.p4suta.register.infrastructure.TestImages;
import io.github.p4suta.register.infrastructure.registrar.MainColumnDetector;
import io.github.p4suta.shared.imaging.Pix;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FFM-backed end-to-end test that a --diag run writes a valid overlay, JSONL log and summary. */
class DiagnosticsTest {

    @Test
    void writesOverlayJsonlAndSummary(@TempDir Path tmp) throws Exception {
        boolean[][] page = TestImages.blank(200, 300);
        TestImages.fillRect(page, 40, 30, 160, 270);
        Path src = tmp.resolve("page-000.pbm");
        TestImages.writePbm(src, page);

        Path diagDir = tmp.resolve("diag");
        Diagnostics diagnostics = new Diagnostics(diagDir, false);
        Box ref;
        try (Pix work = Pix.read(src)) {
            Detection det = new MainColumnDetector().detect(work, 300).orElseThrow();
            ref = det.column();
            diagnostics.addPage(samplePage(0, work, det), src);
        }
        RunInfo info = new RunInfo("a6", 300, 240, 360, true, "CENTER", 0.5, 1, 1, ref, ref);
        diagnostics.finish(info, List.of(src));

        Path overlay = diagDir.resolve("0000-page-000.diag.webp");
        Path jsonl = diagDir.resolve("pages.jsonl");
        Path summary = diagDir.resolve("summary.txt");
        Path corpusOverlay = diagDir.resolve("corpus-overlay.webp");
        Path residuals = diagDir.resolve("residuals.webp");
        assertTrue(Files.exists(overlay), "overlay webp written");
        assertTrue(Files.exists(jsonl), "jsonl written");
        assertTrue(Files.exists(summary), "summary written");
        assertTrue(Files.exists(corpusOverlay), "corpus overlay written");
        assertTrue(Files.exists(residuals), "residual chart written");
        // No PNG artifacts are left behind — every human-facing still is lossless WebP.
        assertFalse(Files.exists(diagDir.resolve("0000-page-000.diag.png")), "no PNG overlay");
        assertFalse(Files.exists(diagDir.resolve("corpus-overlay.png")), "no PNG corpus overlay");
        assertFalse(Files.exists(diagDir.resolve("residuals.png")), "no PNG residual chart");

        assertEquals(1, Files.readString(jsonl).lines().count());
        assertTrue(Files.readString(jsonl).contains("\"index\":0"));
        assertTrue(Files.readString(summary).contains("pages: 1"));

        // Read the WebP artifacts back through Leptonica (ImageIO has no WebP plugin) — this both
        // checks the dimensions and proves the lossless WebP encode is decodable.
        assertSaneDimensions(overlay, 200, 200, "overlay");
        assertSaneDimensions(corpusOverlay, 400, 400, "corpus overlay");
        assertSaneDimensions(residuals, 400, 400, "residual chart");
    }

    @Test
    void writesFlipbookCoveringBothFrameSizes(@TempDir Path tmp) throws Exception {
        boolean[][] shortPage = TestImages.blank(200, 300);
        TestImages.fillRect(shortPage, 40, 30, 160, 270);
        Path shortSrc = tmp.resolve("page-000.pbm");
        TestImages.writePbm(shortSrc, shortPage);

        // A frame taller than MAX_FRAME_HEIGHT (1000) drives the downscale branch of writeFlipbook;
        // the short page drives the pass-through branch.
        boolean[][] tallPage = TestImages.blank(200, 1100);
        TestImages.fillRect(tallPage, 40, 30, 160, 1070);
        Path tallSrc = tmp.resolve("page-001.pbm");
        TestImages.writePbm(tallSrc, tallPage);

        // img2webp requires every frame in one animation to share dimensions (the real pipeline
        // always feeds uniform canvas pages), so the two frame-size branches are exercised in
        // separate flip-books: a uniform multi-frame book (pass-through) and a single down-scaled
        // tall frame.
        Path uniformDir =
                runFlipbook(tmp.resolve("uniform"), shortSrc, List.of(shortSrc, shortSrc));
        Path tallDir = runFlipbook(tmp.resolve("tall"), shortSrc, List.of(tallSrc));

        assertTrue(Files.exists(uniformDir.resolve("flipbook.webp")), "uniform flip-book written");
        assertTrue(Files.exists(tallDir.resolve("flipbook.webp")), "down-scaled flip-book written");
        // The frame scratch directory is always cleaned up.
        try (Stream<Path> entries = Files.list(tallDir)) {
            assertFalse(
                    entries.anyMatch(p -> p.getFileName().toString().startsWith(".flipbook")),
                    "flip-book frame scratch directory cleaned up");
        }
    }

    /**
     * Run a {@code --flipbook} diagnostics pass into {@code diagDir}: record {@code corpusPage} so
     * the corpus artifacts have a page, then assemble {@code outputs} into the flip-book. Returns
     * {@code diagDir}.
     */
    private static Path runFlipbook(Path diagDir, Path corpusPage, List<Path> outputs)
            throws Exception {
        Diagnostics diagnostics = new Diagnostics(diagDir, true);
        Box ref;
        try (Pix work = Pix.read(corpusPage)) {
            Detection det = new MainColumnDetector().detect(work, 300).orElseThrow();
            ref = det.column();
            diagnostics.addPage(samplePage(0, work, det), corpusPage);
        }
        RunInfo info = new RunInfo("a6", 300, 240, 360, true, "CENTER", 0.5, 1, 1, ref, ref);
        diagnostics.finish(info, outputs);
        return diagDir;
    }

    /** Read {@code webp} through Leptonica and assert it is at least {@code minW}x{@code minH}. */
    private static void assertSaneDimensions(Path webp, int minW, int minH, String what) {
        try (Pix p = Pix.read(webp)) {
            assertTrue(p.width() > minW && p.height() > minH, what + " has sane dimensions");
        }
    }

    private static PageDiagnostic samplePage(int index, Pix work, Detection det) {
        return new PageDiagnostic(
                index,
                Parity.RECTO,
                "page-000.pbm",
                work.width(),
                work.height(),
                240,
                360,
                true,
                new Skew(0.5, 2.5, true, true),
                new PageDiagnostic.Column(
                        det.column(),
                        det.verticalBand().start(),
                        det.verticalBand().endExclusive()),
                det.column(),
                new PageDiagnostic.Placement(
                        true,
                        false,
                        1.0,
                        10,
                        20,
                        10,
                        20,
                        false,
                        false,
                        work.width(),
                        work.height()));
    }
}
