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

/**
 * End-to-end guard for the cross-page registration the {@code top_right} anchor exists for: pages
 * whose identical text column sits at jittered positions must come out with that column's top-right
 * corner at the <em>same</em> canvas coordinate on every page of a parity. This is the property a
 * reader sees as the first line and the page number sitting on a straight line through the whole
 * book. (The corner is pinned per parity — recto and verso each to their own reference — so the
 * check is within parity, mirroring how a real right-to-left book's two sides have their own
 * margins.)
 */
class TopRightRegistrationE2eTest {

    @Test
    void topRightLandsTheColumnCornerAtOneCanvasCoordinate(@TempDir Path tmp) throws Exception {
        Path input = tmp.resolve("in");
        Path output = tmp.resolve("out");
        Files.createDirectories(input);

        // Same 30x120 column on every page, but shifted a few px each way (scan jitter). Pages are
        // smaller than the A6@100 canvas, so registration is pure translation — no scaling, and the
        // crop path never has to trim a real column.
        int[][] origin = {{38, 8}, {30, 12}, {40, 6}, {32, 10}};
        for (int i = 0; i < origin.length; i++) {
            int x0 = origin[i][0];
            int y0 = origin[i][1];
            boolean[][] page = TestImages.pageWithColumn(100, 140, x0, y0, x0 + 29, y0 + 119);
            TestImages.writePbm(input.resolve("page-%02d.pbm".formatted(i + 1)), page);
        }

        RegisterOptions options =
                new RegisterOptions(
                        OptionalInt.of(100),
                        PaperSize.Standard.A6,
                        false, // deskew off: isolate registration from any spurious tilt finding
                        true,
                        0.5,
                        Anchor.TOP_RIGHT);
        RegistrationService.Config config =
                new RegistrationService.Config(
                        input, output, OutputFormat.SAME, "*.pbm", 2, false, options, null, false);

        TestComposition.registrationService().run(config);

        int expectedWidth = PaperSize.Standard.A6.widthPx(100);
        int expectedHeight = PaperSize.Standard.A6.heightPx(100);
        int[] right = new int[origin.length];
        int[] top = new int[origin.length];
        for (int i = 0; i < origin.length; i++) {
            try (Pix page = Pix.read(output.resolve("page-%02d.pbm".formatted(i + 1)))) {
                assertEquals(expectedWidth, page.width());
                assertEquals(expectedHeight, page.height());
                right[i] = lastInkIndex(page.inkByColumn());
                top[i] = firstInkIndex(page.inkByRow());
            }
        }
        assertTrue(right[0] > 0 && top[0] >= 0, "a column was found on the output pages");
        // Output files page-01..04 are corpus indices 0..3, i.e. recto, verso, recto, verso. Each
        // parity's column corner must land at one canvas coordinate, jitter removed.
        assertEquals(right[0], right[2], "recto column right edge drifts");
        assertEquals(top[0], top[2], "recto column top edge drifts");
        assertEquals(right[1], right[3], "verso column right edge drifts");
        assertEquals(top[1], top[3], "verso column top edge drifts");
    }

    /** The first index whose ink count is positive (the top/left inked edge). */
    private static int firstInkIndex(int[] ink) {
        for (int i = 0; i < ink.length; i++) {
            if (ink[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    /** The last index whose ink count is positive (the bottom/right inked edge). */
    private static int lastInkIndex(int[] ink) {
        for (int i = ink.length - 1; i >= 0; i--) {
            if (ink[i] > 0) {
                return i;
            }
        }
        return -1;
    }
}
