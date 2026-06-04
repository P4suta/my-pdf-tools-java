package io.github.p4suta.register.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The per-run knobs' validation and dpi defaulting. The compact constructor guards the two
 * value-range invariants (positive dpi, outlierRatio in {@code (0, 1]}) and {@link
 * RegisterOptions#canvasDpi()} resolves the empty-dpi run to {@link RegisterOptions#DEFAULT_DPI}.
 */
class RegisterOptionsTest {

    private static RegisterOptions withDpiAndRatio(OptionalInt dpi, double outlierRatio) {
        return new RegisterOptions(
                dpi, null, /* deskew= */ true, /* scale= */ true, outlierRatio, Anchor.TOP_RIGHT);
    }

    @Test
    void rejectsANonPositiveExplicitDpi() {
        assertThrows(IllegalArgumentException.class, () -> withDpiAndRatio(OptionalInt.of(0), 0.5));
        assertThrows(
                IllegalArgumentException.class, () -> withDpiAndRatio(OptionalInt.of(-1), 0.5));
    }

    @Test
    void acceptsAnEmptyDpiWithoutValidatingIt() {
        // The empty (inherit-from-scan) dpi skips the positivity check entirely.
        RegisterOptions options = withDpiAndRatio(OptionalInt.empty(), 0.5);
        assertEquals(OptionalInt.empty(), options.dpi());
    }

    @Test
    void rejectsAnOutlierRatioOutsideTheHalfOpenUnitInterval() {
        // Zero and negatives are out (a column can't be smaller than nothing of the reference)...
        assertThrows(
                IllegalArgumentException.class, () -> withDpiAndRatio(OptionalInt.of(400), 0.0));
        assertThrows(
                IllegalArgumentException.class, () -> withDpiAndRatio(OptionalInt.of(400), -0.1));
        // ...and anything above 1.0 is out (the whole reference area is the upper bound).
        assertThrows(
                IllegalArgumentException.class, () -> withDpiAndRatio(OptionalInt.of(400), 1.0001));
    }

    @Test
    void acceptsTheOutlierRatioBoundaryOfExactlyOne() {
        // 1.0 is the inclusive upper end of (0, 1]; it must be accepted.
        RegisterOptions options = withDpiAndRatio(OptionalInt.of(400), 1.0);
        assertEquals(1.0, options.outlierRatio());
    }

    @Test
    void canvasDpiFallsBackToTheDefaultWhenDpiIsEmpty() {
        RegisterOptions inherited = withDpiAndRatio(OptionalInt.empty(), 0.5);
        assertEquals(RegisterOptions.DEFAULT_DPI, inherited.canvasDpi());
    }

    @Test
    void canvasDpiReturnsTheExplicitDpiWhenPresent() {
        RegisterOptions explicit = withDpiAndRatio(OptionalInt.of(600), 0.5);
        assertEquals(600, explicit.canvasDpi());
    }
}
