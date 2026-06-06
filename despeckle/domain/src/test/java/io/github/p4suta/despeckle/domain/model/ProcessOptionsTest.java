package io.github.p4suta.despeckle.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.shared.kernel.Resolution;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Resolution precedence and speck-size derivation for {@link ProcessOptions}. */
final class ProcessOptionsTest {

    private static ProcessOptions of(OptionalInt dpi, OptionalInt speck) {
        return ProcessOptions.of(dpi, speck, true);
    }

    /** The image-resolution argument for a page tagged at {@code dpi}. */
    private static Optional<Resolution> img(int dpi) {
        return Optional.of(Resolution.of(dpi));
    }

    /** The image-resolution argument for a page that carries no embedded resolution. */
    private static Optional<Resolution> noImg() {
        return Optional.empty();
    }

    @Test
    void explicitSpeckSizeWinsOverEverything() {
        ProcessOptions options = of(OptionalInt.of(300), OptionalInt.of(5));
        assertEquals(5, options.speckSize(img(600)), "explicit speck size ignores the resolution");
        assertEquals(5, options.speckSize(noImg()));
    }

    @Test
    void explicitDpiWinsOverTheImageResolution() {
        ProcessOptions options = of(OptionalInt.of(300), OptionalInt.empty());
        assertEquals(3, options.speckSize(img(600)), "the --dpi flag overrides the embedded 600");
        assertEquals(Optional.of(Resolution.of(300)), options.resolution(img(600)));
    }

    @Test
    void fallsBackToTheImageResolutionWhenNoDpiGiven() {
        ProcessOptions options = of(OptionalInt.empty(), OptionalInt.empty());
        assertEquals(6, options.speckSize(img(600)), "~6 px at 600 dpi");
        assertEquals(3, options.speckSize(img(300)), "~3 px at 300 dpi");
        assertEquals(Optional.of(Resolution.of(600)), options.resolution(img(600)));
    }

    @Test
    void assumesDefaultDpiButReportsNoResolutionWhenNothingIsKnown() {
        ProcessOptions options = of(OptionalInt.empty(), OptionalInt.empty());
        assertEquals(
                3,
                options.speckSize(noImg()),
                "filter assumes DEFAULT_DPI (300) when nothing known");
        assertFalse(
                options.resolution(noImg()).isPresent(),
                "but a guessed resolution is never asserted on output");
    }

    @Test
    void defaultsAutoDetect() {
        ProcessOptions defaults = ProcessOptions.defaults();
        assertFalse(defaults.dpi().isPresent());
        assertFalse(defaults.speckSizePx().isPresent());
        assertTrue(defaults.fillHoles());
        assertTrue(defaults.isolatedDustEnabled(), "the isolated-dust pass is on by default");
    }

    @Test
    void isolatedDustIsEnabledByEitherTheFlagOrAnExplicitSize() {
        assertFalse(of(OptionalInt.empty(), OptionalInt.empty()).isolatedDustEnabled());
        assertTrue(
                new ProcessOptions(
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                true,
                                true,
                                OptionalInt.empty())
                        .isolatedDustEnabled(),
                "the flag alone enables it");
        assertTrue(
                new ProcessOptions(
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                true,
                                false,
                                OptionalInt.of(12))
                        .isolatedDustEnabled(),
                "an explicit size implies it");
    }

    @Test
    void isolatedDustSizeDerivesFromResolutionOrIsOverridden() {
        ProcessOptions derived =
                new ProcessOptions(
                        OptionalInt.empty(), OptionalInt.empty(), true, true, OptionalInt.empty());
        assertEquals(15, derived.isolatedDustSize(img(600)), "~15 px at 600 dpi (dpi/40)");
        assertEquals(
                derived.isolatedDustSize(img(600)) + 6, derived.isolatedDustProximity(img(600)));

        ProcessOptions explicit =
                new ProcessOptions(
                        OptionalInt.empty(), OptionalInt.of(3), true, true, OptionalInt.of(12));
        assertEquals(12, explicit.isolatedDustSize(img(600)), "an explicit size wins");
        assertEquals(
                12 + 3, explicit.isolatedDustProximity(img(600)), "proximity = size + speck size");
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThrows(
                IllegalArgumentException.class, () -> of(OptionalInt.of(0), OptionalInt.empty()));
        assertThrows(
                IllegalArgumentException.class, () -> of(OptionalInt.empty(), OptionalInt.of(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProcessOptions(
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                true,
                                true,
                                OptionalInt.of(0)));
    }

    // Domain floor coverage.

    @Test
    void withDpiPinsAnExplicitResolutionAndPreservesEveryOtherKnob() {
        ProcessOptions base =
                new ProcessOptions(
                        OptionalInt.empty(), OptionalInt.of(7), false, true, OptionalInt.of(9));
        ProcessOptions pinned = base.withDpi(450);
        assertEquals(OptionalInt.of(450), pinned.dpi(), "dpi is set");
        assertEquals(OptionalInt.of(7), pinned.speckSizePx(), "speck size is preserved");
        assertFalse(pinned.fillHoles(), "fillHoles is preserved");
        assertTrue(pinned.removeIsolatedDust(), "removeIsolatedDust is preserved");
        assertEquals(
                OptionalInt.of(9), pinned.isolatedDustSizePx(), "isolated-dust size is preserved");
        assertEquals(
                Optional.of(Resolution.of(450)),
                pinned.resolution(img(72)),
                "the pinned dpi wins over image");
    }

    @Test
    void speckSizeNeverDropsBelowOnePixelAtVeryLowResolution() {
        // round(40 / 100f) == 0, so the Math.max(1, ...) floor must keep it at 1 px.
        ProcessOptions options = of(OptionalInt.of(40), OptionalInt.empty());
        assertEquals(1, options.speckSize(noImg()), "the speck size floors at 1 px");
    }

    @Test
    void isolatedDustSizeStaysAboveTheSpeckSizeAtVeryLowResolution() {
        // At 40 dpi: speckSize == 1 and round(40 / 40f) == 1, so the speckSize+1 floor (== 2) wins.
        ProcessOptions options =
                new ProcessOptions(
                        OptionalInt.of(40), OptionalInt.empty(), true, true, OptionalInt.empty());
        assertEquals(
                2, options.isolatedDustSize(noImg()), "isolated-dust size floors at speckSize + 1");
    }
}
