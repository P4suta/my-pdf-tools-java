package io.github.p4suta.shared.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResolutionTest {

    @Test
    void ofExposesTheDpi() {
        assertEquals(300, Resolution.of(300).dpi());
    }

    @Test
    void rejectsNonPositiveDpi() {
        IllegalArgumentException zero =
                assertThrows(IllegalArgumentException.class, () -> Resolution.of(0));
        assertEquals("dpi must be positive: 0", zero.getMessage());

        IllegalArgumentException negative =
                assertThrows(IllegalArgumentException.class, () -> Resolution.of(-1));
        assertEquals("dpi must be positive: -1", negative.getMessage());
    }

    @Test
    void pxFromMmIsExactWhenTheArithmeticIsExact() {
        // 254 dpi = exactly 10 px/mm.
        assertEquals(2540, Resolution.of(254).pxFromMm(254.0));
        assertEquals(100, Resolution.of(254).pxFromMm(10.0));
    }

    @Test
    void pxFromMmMatchesRegistersPaperSizeWidthPxExactly() {
        // Parity anchor: this is exactly register's PaperSizeTest assertion
        // (SHIROKU.widthPx(254) == 1270, .heightPx(254) == 1880). The migration substitution must
        // produce the identical value.
        Resolution r = Resolution.of(254);
        assertEquals(1270, r.pxFromMm(127.0));
        assertEquals(1880, r.pxFromMm(188.0));
    }

    @Test
    void pxFromMmRoundsHalfUpNotTruncates() {
        // At 127 dpi, 0.1 mm = 0.1 * 127 / 25.4 = 0.5 px exactly.
        // Math.round(0.5) == 1; a (int) truncation would give 0. This distinguishes the two.
        assertEquals(1, Resolution.of(127).pxFromMm(0.1));
    }

    @Test
    void pxFromMmRoundsToNearest() {
        // At 300 dpi, 1 mm = 300 / 25.4 = 11.811... px -> rounds to 12.
        assertEquals(12, Resolution.of(300).pxFromMm(1.0));
        // 100 mm at 300 dpi = 1181.10... -> 1181.
        assertEquals(1181, Resolution.of(300).pxFromMm(100.0));
    }

    @Test
    void pxFromMmOfZeroIsZero() {
        assertEquals(0, Resolution.of(600).pxFromMm(0.0));
    }

    @Test
    void pxFromInchScalesByDpi() {
        assertEquals(300, Resolution.of(300).pxFromInch(1.0));
        assertEquals(150, Resolution.of(300).pxFromInch(0.5));
        // 0.5 px rounds half-up to 1.
        assertEquals(1, Resolution.of(1).pxFromInch(0.5));
    }

    @Test
    void mmFromPxIsTheInverseConversion() {
        // 10 px/mm at 254 dpi.
        assertEquals(10.0, Resolution.of(254).mmFromPx(100));
        assertEquals(0.0, Resolution.of(300).mmFromPx(0));
    }

    @Test
    void mmFromPxRoundTripsThroughPxFromMmAtExactResolutions() {
        Resolution r = Resolution.of(254); // exact 10 px/mm
        int px = r.pxFromMm(50.0);
        assertEquals(50.0, r.mmFromPx(px));
    }
}
