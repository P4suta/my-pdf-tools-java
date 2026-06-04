package io.github.p4suta.register.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.p4suta.register.TestComposition;
import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PaperSize;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.TestImages;
import io.github.p4suta.shared.imaging.Pix;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end for auto paper detection (no {@code --paper}). Pages scanned a few millimeters under
 * nominal A6 (trim/shrink) must still resolve to A6's clean nominal size — the canvas comes out at
 * A6 dimensions, not the raw scanned size a {@link PaperSize.Custom} fallback would have produced.
 */
class AutoPaperE2eTest {

    @Test
    void autoPaperSnapsTheScannedSizeToItsStandard(@TempDir Path tmp) throws Exception {
        Path input = tmp.resolve("in");
        Path output = tmp.resolve("out");
        Files.createDirectories(input);

        // 579x862 px at 150 dpi = 98.0 x 146.0 mm: a 文庫 (A6) trimmed a few mm under nominal.
        int dpi = 150;
        int pageW = 579;
        int pageH = 862;
        for (int i = 0; i < 4; i++) {
            int x0 = 180 + i; // a little jitter
            boolean[][] page = TestImages.pageWithColumn(pageW, pageH, x0, 80, x0 + 220, 780);
            TestImages.writePbm(input.resolve("page-%02d.pbm".formatted(i + 1)), page);
        }

        RegisterOptions options =
                new RegisterOptions(
                        OptionalInt.of(dpi),
                        null, // no --paper: auto-detect from the scan
                        false,
                        true,
                        0.5,
                        Anchor.TOP_RIGHT);
        RegistrationService.Config config =
                new RegistrationService.Config(
                        input, output, OutputFormat.SAME, "*.pbm", 2, false, options, null, false);

        TestComposition.registrationService().run(config);

        // A6 nominal at 150 dpi — distinct from the 579x862 raw scan, so this proves it snapped to
        // the standard rather than falling back to the exact scanned (custom) size.
        int expectedWidth = PaperSize.Standard.A6.widthPx(dpi);
        int expectedHeight = PaperSize.Standard.A6.heightPx(dpi);
        try (Pix page = Pix.read(output.resolve("page-01.pbm"))) {
            assertEquals(expectedWidth, page.width(), "canvas width snapped to A6");
            assertEquals(expectedHeight, page.height(), "canvas height snapped to A6");
        }
    }
}
