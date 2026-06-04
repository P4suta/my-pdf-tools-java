package io.github.p4suta.register.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** End-to-end: a directory of jittered pages is registered onto one uniform canvas. */
class MainE2eTest {

    @Test
    void registersDirectoryOntoUniformCanvas(@TempDir Path tmp) throws Exception {
        Path input = tmp.resolve("in");
        Path output = tmp.resolve("out");
        Files.createDirectories(input);

        // Four pages whose single text column sits at slightly different x positions (scan jitter).
        int[][] columnX = {{38, 68}, {30, 60}, {40, 70}, {32, 62}};
        for (int i = 0; i < columnX.length; i++) {
            boolean[][] page =
                    TestImages.pageWithColumn(100, 140, columnX[i][0], 10, columnX[i][1], 130);
            TestImages.writePbm(input.resolve("page-%02d.pbm".formatted(i + 1)), page);
        }

        RegisterOptions options =
                new RegisterOptions(
                        OptionalInt.of(100), PaperSize.Standard.A6, true, true, 0.5, Anchor.CENTER);
        // --format SAME with deskew on: exercises that the source format is read from the original
        // page (the deskewed PIX has lost it), so the .pbm output is still valid binary PBM.
        RegistrationService.Config config =
                new RegistrationService.Config(
                        input, output, OutputFormat.SAME, "*.pbm", 2, false, options, null, false);

        RegistrationService.Summary summary = TestComposition.registrationService().run(config);

        assertEquals(4, summary.pages());
        int expectedWidth = PaperSize.Standard.A6.widthPx(100);
        int expectedHeight = PaperSize.Standard.A6.heightPx(100);
        for (int i = 1; i <= 4; i++) {
            Path out = output.resolve("page-%02d.pbm".formatted(i));
            assertTrue(Files.exists(out), "missing output " + out);
            try (Pix page = Pix.read(out)) {
                assertEquals(expectedWidth, page.width());
                assertEquals(expectedHeight, page.height());
            }
        }
    }

    @Test
    void inheritsTheInputScanResolutionWhenNoDpiGiven(@TempDir Path tmp) throws Exception {
        Path input = tmp.resolve("in");
        Path output = tmp.resolve("out");
        Files.createDirectories(input);

        // PNG inputs tagged at 300 dpi (as stamp-dpi.py would tag pdfimages output). With no --dpi,
        // the canvas must size to that 300 dpi, not the 400-dpi default.
        int scanDpi = 300;
        for (int i = 0; i < 2; i++) {
            boolean[][] page = TestImages.pageWithColumn(100, 140, 35, 10, 65, 130);
            Path pbm = tmp.resolve("page-%02d.pbm".formatted(i));
            TestImages.writePbm(pbm, page);
            try (Pix p = Pix.read(pbm)) {
                p.setResolution(scanDpi);
                p.writePng(input.resolve("page-%02d.png".formatted(i)));
            }
        }

        RegisterOptions options =
                new RegisterOptions(
                        OptionalInt.empty(), // no --dpi: inherit the inputs' 300 dpi
                        PaperSize.Standard.A6,
                        true,
                        true,
                        0.5,
                        Anchor.TOP_RIGHT);
        RegistrationService.Config config =
                new RegistrationService.Config(
                        input, output, OutputFormat.PNG, "*.png", 2, false, options, null, false);

        TestComposition.registrationService().run(config);

        int expectedWidth = PaperSize.Standard.A6.widthPx(scanDpi);
        int expectedHeight = PaperSize.Standard.A6.heightPx(scanDpi);
        try (Pix page = Pix.read(output.resolve("page-00.png"))) {
            assertEquals(expectedWidth, page.width(), "canvas width inherited the 300-dpi scan");
            assertEquals(expectedHeight, page.height(), "canvas height inherited the 300-dpi scan");
            assertEquals(
                    scanDpi, page.resolution(), "output is tagged at the inherited resolution");
        }
    }
}
