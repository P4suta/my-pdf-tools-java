package io.github.p4suta.register.infrastructure.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
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
            PageDiagnostic pd =
                    new PageDiagnostic(
                            0,
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
                            ref,
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
            diagnostics.addPage(pd, src);
        }
        RunInfo info = new RunInfo("a6", 300, 240, 360, true, "CENTER", 0.5, 1, 1, ref, ref);
        diagnostics.finish(info, List.of(src));

        Path overlay = diagDir.resolve("0000-page-000.diag.png");
        Path jsonl = diagDir.resolve("pages.jsonl");
        Path summary = diagDir.resolve("summary.txt");
        Path corpusOverlay = diagDir.resolve("corpus-overlay.png");
        Path residuals = diagDir.resolve("residuals.png");
        assertTrue(Files.exists(overlay), "overlay png written");
        assertTrue(Files.exists(jsonl), "jsonl written");
        assertTrue(Files.exists(summary), "summary written");
        assertTrue(Files.exists(corpusOverlay), "corpus overlay written");
        assertTrue(Files.exists(residuals), "residual chart written");

        assertEquals(1, Files.readString(jsonl).lines().count());
        assertTrue(Files.readString(jsonl).contains("\"index\":0"));
        assertTrue(Files.readString(summary).contains("pages: 1"));

        BufferedImage img = ImageIO.read(overlay.toFile());
        assertTrue(img.getWidth() > 200 && img.getHeight() > 200, "overlay has sane dimensions");
        BufferedImage corpus = ImageIO.read(corpusOverlay.toFile());
        assertTrue(
                corpus.getWidth() > 400 && corpus.getHeight() > 400,
                "corpus overlay has sane dimensions");
        BufferedImage chart = ImageIO.read(residuals.toFile());
        assertTrue(
                chart.getWidth() > 400 && chart.getHeight() > 400,
                "residual chart has sane dimensions");
    }
}
