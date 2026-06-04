package io.github.p4suta.register.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class PaperSizeTest {

    @Test
    void shirokuIsTheDefaultBookSize() {
        assertEquals(127.0, PaperSize.Standard.SHIROKU.widthMm());
        assertEquals(188.0, PaperSize.Standard.SHIROKU.heightMm());
    }

    @Test
    void convertsMillimetersToPixels() {
        // 254 dpi = exactly 10 px/mm, so the arithmetic is exact.
        assertEquals(1270, PaperSize.Standard.SHIROKU.widthPx(254));
        assertEquals(1880, PaperSize.Standard.SHIROKU.heightPx(254));
    }

    @Test
    void parsesStandardNamesCaseInsensitively() {
        assertEquals(PaperSize.Standard.A4, PaperSize.parse("a4"));
        assertEquals(PaperSize.Standard.SHINSHO, PaperSize.parse("SHINSHO"));
    }

    @Test
    void parsesCustomMillimeterSpec() {
        PaperSize parsed = PaperSize.parse("127x188");
        PaperSize.Custom custom = assertInstanceOf(PaperSize.Custom.class, parsed);
        assertEquals(127.0, custom.widthMm());
        assertEquals(188.0, custom.heightMm());
    }

    @Test
    void fromScanSnapsAnExactSizeToItsStandard() {
        assertEquals(PaperSize.Standard.A6, PaperSize.fromScan(105, 148));
        assertEquals(PaperSize.Standard.SHIROKU, PaperSize.fromScan(127, 188));
    }

    @Test
    void fromScanSnapsATrimmedOrShrunkBookToItsNominalStandard() {
        // A bound book scanned a few mm under nominal (trim/shrink) still resolves to its standard.
        assertEquals(PaperSize.Standard.A6, PaperSize.fromScan(98, 146.4)); // narrow 文庫
        assertEquals(PaperSize.Standard.SHIROKU, PaperSize.fromScan(120, 187)); // narrow 四六判
    }

    @Test
    void fromScanDoesNotOvershootWhenSlightlyOversized() {
        // A hair larger than A6 must stay A6, not jump to the next size up.
        assertEquals(PaperSize.Standard.A6, PaperSize.fromScan(107, 150));
    }

    @Test
    void fromScanSnapsALooselyCroppedBookBackDownToItsStandard() {
        // A 文庫 (A6) scanned several mm over nominal — loose crop / scan margin — is nearer B6 by
        // raw size, but the excess is croppable margin, so it must still resolve to A6, not B6.
        assertEquals(PaperSize.Standard.A6, PaperSize.fromScan(118, 165));
    }

    @Test
    void fromScanKeepsAGenuinelyLargerBookAtItsOwnStandard() {
        // A real B6 measured near nominal must stay B6 — the oversize discount only breaks ties
        // toward the smaller size, it does not pull an accurately-sized book down.
        assertEquals(PaperSize.Standard.B6, PaperSize.fromScan(128, 182));
    }

    @Test
    void fromScanSnapsRightUpToTheAutoSnapToleranceBoundary() {
        // One axis exactly nominal (width 105 == A6 width, exercising the measured-equals-nominal
        // arm of axisDeviation), the other a hair inside AUTO_SNAP_TOLERANCE (avg deviation just
        // below 0.08). It must still snap to A6 — the boundary is inclusive.
        assertEquals(PaperSize.Standard.A6, PaperSize.fromScan(105, 124.5));
    }

    @Test
    void fromScanDropsToCustomJustBeyondTheAutoSnapTolerance() {
        // The same shape a hair past the boundary (avg deviation just above 0.08): no standard is
        // close enough, so the exact measured size is kept as a Custom.
        PaperSize.Custom custom =
                assertInstanceOf(PaperSize.Custom.class, PaperSize.fromScan(105, 124.0));
        assertEquals(105.0, custom.widthMm());
        assertEquals(124.0, custom.heightMm());
    }

    @Test
    void fromScanFallsBackToCustomWhenNoStandardIsClose() {
        PaperSize.Custom custom =
                assertInstanceOf(PaperSize.Custom.class, PaperSize.fromScan(160, 160));
        assertEquals(160.0, custom.widthMm());
        assertEquals(160.0, custom.heightMm());
    }
}
