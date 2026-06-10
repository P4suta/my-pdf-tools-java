package io.github.p4suta.shared.imaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FFM smoke test plus functional coverage of the primitive ops on {@link Pix}. Requires the native
 * Leptonica library, which the dev container bundles.
 */
final class PixTest {

    // FFM smoke: round-trip a synthetic 1 bpp PBM

    @Test
    void pbmRoundTripIsPixelIdentical(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src.pbm");
        Path dst = dir.resolve("dst.pbm");
        boolean[][] img = TestImages.blank(24, 24);
        TestImages.fillRect(img, 2, 2, 9, 20);
        TestImages.writePbm(src, img);

        try (Pix a = Pix.read(src)) {
            assertEquals(24, a.width());
            assertEquals(24, a.height());
            a.write(dst, Leptonica.IFF_PNM);
            try (Pix b = Pix.read(dst)) {
                assertTrue(a.pixelsEqual(b), "PBM round-trip must be pixel-identical");
            }
        }
    }

    // metadata

    @Test
    void resolutionStampSurvivesPngAndInputFormatIsReported(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("res.pbm");
        Path png = dir.resolve("res.png");
        boolean[][] img = TestImages.blank(16, 16);
        TestImages.fillRect(img, 4, 4, 11, 11);
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            // A PBM source carries no resolution; the input format is some defined IFF code.
            assertEquals(0, pix.resolution(), "a PBM source carries no resolution");
            assertTrue(pix.inputFormat() >= 0, "input format is a defined IFF code");
            pix.setResolution(600);
            pix.writePng(png);
        }
        try (Pix reread = Pix.read(png)) {
            assertEquals(600, reread.resolution(), "the stamped resolution survives the PNG");
        }
    }

    @Test
    void writeTiffG4RoundTrips(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("g4.pbm");
        Path tiff = dir.resolve("g4.tif");
        boolean[][] img = TestImages.blank(20, 20);
        TestImages.fillRect(img, 3, 3, 16, 16);
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            pix.writeTiffG4(tiff);
        }
        try (Pix back = Pix.read(tiff)) {
            assertEquals(20, back.width());
            assertEquals(20, back.height());
        }
    }

    @Test
    void writeWebpRoundTrips(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("src.pbm");
        Path webp = dir.resolve("out.webp");
        boolean[][] img = TestImages.blank(20, 28);
        TestImages.fillRect(img, 3, 3, 16, 24);
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            pix.writeWebp(webp);
        }
        assertTrue(java.nio.file.Files.size(webp) > 0, "lossless WebP must be written");
        try (Pix back = Pix.read(webp)) {
            assertEquals(20, back.width());
            assertEquals(28, back.height());
        }
    }

    // projection profiles

    @Test
    void inkProfilesTrackTheForeground(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("col.pbm");
        boolean[][] img = TestImages.blank(60, 80);
        TestImages.fillRect(img, 20, 10, 39, 69); // a tall column
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            int[] rows = pix.inkByRow();
            assertEquals(80, rows.length);
            assertEquals(0, rows[0]);
            assertTrue(rows[40] > 0, "the column spans row 40");

            int[] columns = pix.inkByColumn();
            assertEquals(60, columns.length);
            assertEquals(0, columns[0]);
            assertTrue(columns[30] > 0, "the column covers x=30");
        }
    }

    // geometry: blank canvas + blit, clip, scale

    @Test
    void blankCanvasBlitClipAndScale(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("col.pbm");
        boolean[][] img = TestImages.blank(40, 50);
        TestImages.fillRect(img, 10, 5, 29, 44);
        TestImages.writePbm(src, img);

        try (Pix page = Pix.read(src);
                Pix canvas = Pix.blankCanvas(80, 100)) {
            assertEquals(80, canvas.width());
            assertEquals(100, canvas.height());
            canvas.blit(page, 20, 25);

            try (Pix clip = canvas.clip(20, 25, 40, 50)) {
                assertEquals(40, clip.width());
                assertEquals(50, clip.height());
                try (Pix scaledH = clip.scaleToHeight(25)) {
                    assertEquals(25, scaledH.height());
                }
                try (Pix scaledWh = clip.scaleToSize(20, 30)) {
                    assertEquals(20, scaledWh.width());
                    assertEquals(30, scaledWh.height());
                }
            }
        }
    }

    // rotation: orthogonal, arbitrary, raw skew estimate

    @Test
    void rotateOrthAndRotatePreserveOrSwapDimensions(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("col.pbm");
        boolean[][] img = TestImages.blank(60, 80);
        TestImages.fillRect(img, 20, 10, 39, 69);
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            // A single 90-degree turn swaps width and height.
            try (Pix quarter = pix.rotateOrth(1)) {
                assertEquals(80, quarter.width());
                assertEquals(60, quarter.height());
            }
            // An arbitrary same-size sampling rotation keeps the dimensions.
            try (Pix tilted = pix.rotate(Math.toRadians(2.0))) {
                assertEquals(60, tilted.width());
                assertEquals(80, tilted.height());
            }
        }
    }

    @Test
    void findSkewIsRawAndFinite(@TempDir Path dir) throws Exception {
        // findSkew runs Leptonica's row-projection finder directly, with no confidence gating
        // (that is app policy).
        Path pbm = dir.resolve("col.pbm");
        boolean[][] img = TestImages.blank(60, 80);
        TestImages.fillRect(img, 20, 10, 39, 69);
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            Pix.SkewEstimate est = pix.findSkew();
            assertTrue(Double.isFinite(est.angleDeg()), "angle is finite");
            assertTrue(Double.isFinite(est.conf()), "confidence is finite");
            // The record exposes `found` without applying any threshold; just reading it is enough.
            assertEquals(est.found(), est.found());
        }
    }

    // counting + size-select

    @Test
    void countingAndRawSelectBySize(@TempDir Path dir) throws Exception {
        // A 1px-wide, 18px-tall stroke plus a 2x2 dust speck: two components.
        Path pbm = dir.resolve("mix.pbm");
        boolean[][] img = TestImages.blank(32, 32);
        TestImages.fillRect(img, 5, 4, 5, 21); // 1 x 18 stroke
        TestImages.fillRect(img, 20, 20, 21, 21); // 2 x 2 speck
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm)) {
            assertEquals(2, pix.connectedComponents());
            assertEquals(18L + 4L, pix.blackPixels());

            // Raw selectBySize with (IF_EITHER, IF_GT) keeps components larger than 3 on either
            // axis: the tall stroke survives, the speck (small on both) is dropped.
            try (Pix kept =
                    pix.selectBySize(
                            3,
                            3,
                            Leptonica.CONN_8,
                            Leptonica.L_SELECT_IF_EITHER,
                            Leptonica.L_SELECT_IF_GT)) {
                assertEquals(1, kept.connectedComponents(), "tall stroke kept, speck removed");
                assertEquals(18L, kept.blackPixels(), "exactly the stroke's pixels remain");
            }
        }
    }

    // boolean / morphology ops

    @Test
    void invertIsReversible(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("inv.pbm");
        boolean[][] img = TestImages.blank(20, 12);
        TestImages.fillRect(img, 3, 3, 8, 8);
        TestImages.writePbm(pbm, img);

        try (Pix pix = Pix.read(pbm);
                Pix once = pix.inverted();
                Pix twice = once.inverted()) {
            assertFalse(pix.pixelsEqual(once), "single inversion changes the image");
            assertTrue(pix.pixelsEqual(twice), "double inversion restores the image");
        }
    }

    @Test
    void booleanAndMorphologyOps(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("shapes.pbm");
        boolean[][] img = TestImages.blank(32, 32);
        TestImages.fillRect(img, 4, 4, 12, 12); // block A
        TestImages.fillRect(img, 18, 18, 26, 26); // block B
        TestImages.writePbm(pbm, img);

        Path otherPbm = dir.resolve("a.pbm");
        boolean[][] aOnly = TestImages.blank(32, 32);
        TestImages.fillRect(aOnly, 4, 4, 12, 12); // just block A
        TestImages.writePbm(otherPbm, aOnly);

        try (Pix both = Pix.read(pbm);
                Pix a = Pix.read(otherPbm)) {
            // AND with A keeps only A; OR with A is unchanged (A is a subset of both).
            try (Pix intersection = both.and(a)) {
                assertTrue(intersection.pixelsEqual(a), "AND with a subset yields the subset");
            }
            try (Pix union = both.or(a)) {
                assertTrue(union.pixelsEqual(both), "OR with a subset is unchanged");
            }
            // SUBTRACT A from both leaves only B's pixels (81 px = 9x9 block).
            try (Pix justB = both.subtract(a)) {
                assertEquals(81L, justB.blackPixels(), "subtracting A leaves only B");
            }
            // dilate grows the foreground; open by a radius the speck can't survive shrinks it.
            long base = both.blackPixels();
            try (Pix grown = both.dilated(1)) {
                assertTrue(grown.blackPixels() > base, "dilation grows the foreground");
            }
            try (Pix openedKept = both.opened(1)) {
                assertTrue(
                        openedKept.blackPixels() > 0,
                        "opening solid 9x9 blocks by radius 1 keeps them");
            }
        }
    }

    // negative / lifecycle branches

    @Test
    void readingAMissingFileThrows(@TempDir Path dir) {
        Path missing = dir.resolve("nope.pbm");
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> Pix.read(missing));
        assertTrue(
                String.valueOf(ex.getMessage()).contains("could not read image"),
                "names the unreadable path");
    }

    @Test
    void useAfterCloseThrows(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("x.pbm");
        boolean[][] img = TestImages.blank(8, 8);
        TestImages.fillRect(img, 1, 1, 6, 6);
        TestImages.writePbm(pbm, img);

        Pix pix = Pix.read(pbm);
        pix.close();
        // A second close is a no-op (the handle is already null); a use is a clear failure.
        pix.close();
        assertThrows(IllegalStateException.class, pix::width);
    }

    // DWA vs generic morphology: the empirical gate for the fast path

    /**
     * The load-bearing equality sweep: {@code dilated} runs on the DWA fast path for every size
     * (single safe kernel up to 15, composed safe passes beyond — Leptonica's generated DWA sels
     * are incomplete above 15 and silently diverge there, so the composition is the only exact
     * route), and {@code opened} up to 15. Both must be pixel-identical to the generic rasterop
     * morphology — including at the image borders, where DWA's internal bordering is the classic
     * divergence trap. The fixture has ink touching all four borders, interior glyphs and isolated
     * dots; the radii sweep covers the production sizes (7x7 open, 43x43 dilate) and the 63x63
     * ceiling. A failure here means the fast path may not ship.
     */
    @Test
    void dwaMorphologyMatchesGenericBrickIncludingBorders(@TempDir Path dir) throws Exception {
        Path pbm = dir.resolve("border-ink.pbm");
        boolean[][] img = TestImages.blank(200, 150);
        // Ink on all four borders: full-width top/bottom lines, full-height left/right lines.
        TestImages.fillRect(img, 0, 0, 199, 1);
        TestImages.fillRect(img, 0, 148, 199, 149);
        TestImages.fillRect(img, 0, 0, 1, 149);
        TestImages.fillRect(img, 198, 0, 199, 149);
        // Corner blocks, interior glyphs, isolated dots.
        TestImages.fillRect(img, 0, 0, 8, 8);
        TestImages.fillRect(img, 190, 140, 199, 149);
        TestImages.fillRect(img, 40, 30, 80, 90);
        TestImages.fillRect(img, 120, 50, 150, 60);
        TestImages.fillRect(img, 100, 110, 100, 110);
        TestImages.fillRect(img, 20, 130, 20, 130);
        TestImages.writePbm(pbm, img);

        int[] radii = {0, 1, 3, 7, 10, 15, 21, 31};
        try (Pix page = Pix.read(pbm)) {
            for (int radius : radii) {
                try (Pix dwa = page.dilated(radius);
                        Pix generic = page.dilatedGeneric(radius)) {
                    assertTrue(
                            dwa.pixelsEqual(generic),
                            "dilate radius " + radius + " must be pixel-identical");
                }
                try (Pix dwa = page.opened(radius);
                        Pix generic = page.openedGeneric(radius)) {
                    assertTrue(
                            dwa.pixelsEqual(generic),
                            "open radius " + radius + " must be pixel-identical");
                }
            }
        }
    }

    /** The sweep on degenerate pages: smaller than DWA's border, all-black, and all-white. */
    @Test
    void dwaMorphologyMatchesGenericOnDegeneratePages(@TempDir Path dir) throws Exception {
        boolean[][] tiny = TestImages.blank(20, 20);
        TestImages.fillRect(tiny, 0, 0, 19, 2);
        TestImages.fillRect(tiny, 17, 0, 19, 19);
        TestImages.fillRect(tiny, 9, 9, 9, 9);
        boolean[][] black = TestImages.blank(50, 40);
        TestImages.fillRect(black, 0, 0, 49, 39);
        boolean[][] white = TestImages.blank(50, 40);

        int page = 0;
        for (boolean[][] img : java.util.List.of(tiny, black, white)) {
            Path pbm = dir.resolve("degenerate-" + page++ + ".pbm");
            TestImages.writePbm(pbm, img);
            try (Pix pix = Pix.read(pbm)) {
                for (int radius : new int[] {1, 3, 21}) {
                    try (Pix dwa = pix.dilated(radius);
                            Pix generic = pix.dilatedGeneric(radius)) {
                        assertTrue(dwa.pixelsEqual(generic), "dilate r=" + radius);
                    }
                    try (Pix dwa = pix.opened(radius);
                            Pix generic = pix.openedGeneric(radius)) {
                        assertTrue(dwa.pixelsEqual(generic), "open r=" + radius);
                    }
                }
            }
        }
    }
}
