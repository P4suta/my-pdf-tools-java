package io.github.p4suta.tateyokopdf.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.p4suta.tateyokopdf.domain.model.PageDimension;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import io.github.p4suta.tateyokopdf.domain.model.SpreadHalf;
import io.github.p4suta.tateyokopdf.domain.model.SpreadLayout;
import org.junit.jupiter.api.Test;

final class SpreadLayoutCalculatorTest {

    private static final float EPS = 0.001f;

    private final SpreadLayoutCalculator calc = new SpreadLayoutCalculator();

    @Test
    void rtlEqualSizedPair() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, dim, dim);
        assertThat(layout.spec().widthPt()).isEqualTo(200f);
        assertThat(layout.spec().heightPt()).isEqualTo(200f);
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(100f, within(EPS));
        assertThat(layout.secondPosition()).isPresent();
        assertThat(layout.secondPosition().get().offsetXPt()).isZero();
    }

    @Test
    void ltrEqualSizedPair() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.LTR, dim, dim);
        assertThat(layout.firstPosition().offsetXPt()).isZero();
        assertThat(layout.secondPosition().orElseThrow().offsetXPt()).isEqualTo(100f, within(EPS));
    }

    @Test
    void verticalCenteringWhenHeightsDiffer() {
        var first = new PageDimension(100f, 200f);
        var second = new PageDimension(100f, 100f);
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, first, second);
        assertThat(layout.spec().heightPt()).isEqualTo(200f);
        assertThat(layout.firstPosition().offsetYPt()).isZero();
        assertThat(layout.secondPosition().orElseThrow().offsetYPt()).isEqualTo(50f, within(EPS));
    }

    @Test
    void horizontalCenteringWhenWidthsDiffer() {
        var first = new PageDimension(80f, 100f);
        var second = new PageDimension(100f, 100f);
        SpreadLayout layout = calc.calculate(ReadingDirection.LTR, first, second);
        assertThat(layout.spec().widthPt()).isEqualTo(200f);
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(10f, within(EPS));
        assertThat(layout.secondPosition().orElseThrow().offsetXPt()).isEqualTo(100f, within(EPS));
    }

    @Test
    void singlePagePlacedOnLeadingHalfRtl() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.RTL, dim, SpreadHalf.LEADING);
        assertThat(layout.spec().widthPt()).isEqualTo(200f);
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(100f, within(EPS));
        assertThat(layout.secondPosition()).isEmpty();
    }

    @Test
    void singlePagePlacedOnLeadingHalfLtr() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.LTR, dim, SpreadHalf.LEADING);
        assertThat(layout.firstPosition().offsetXPt()).isZero();
        assertThat(layout.secondPosition()).isEmpty();
    }

    @Test
    void portraitPair() {
        var portrait = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, portrait, portrait);
        assertThat(layout.spec().widthPt()).isEqualTo(200f);
        assertThat(layout.spec().heightPt()).isEqualTo(200f);
    }

    @Test
    void landscapePair() {
        var landscape = new PageDimension(200f, 100f);
        SpreadLayout layout = calc.calculate(ReadingDirection.LTR, landscape, landscape);
        assertThat(layout.spec().widthPt()).isEqualTo(400f);
        assertThat(layout.spec().heightPt()).isEqualTo(100f);
    }

    @Test
    void firstLargerPair() {
        var big = new PageDimension(120f, 200f);
        var small = new PageDimension(100f, 150f);
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, big, small);
        assertThat(layout.spec().widthPt()).isEqualTo(240f);
        assertThat(layout.spec().heightPt()).isEqualTo(200f);
    }

    @Test
    void secondLargerPair() {
        var small = new PageDimension(100f, 150f);
        var big = new PageDimension(120f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.LTR, small, big);
        assertThat(layout.spec().widthPt()).isEqualTo(240f);
        assertThat(layout.spec().heightPt()).isEqualTo(200f);
    }

    @Test
    void rtlPairFirstOnRight() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, dim, dim);
        assertThat(layout.firstPosition().offsetXPt())
                .isGreaterThan(layout.secondPosition().orElseThrow().offsetXPt());
    }

    @Test
    void ltrPairFirstOnLeft() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.LTR, dim, dim);
        assertThat(layout.firstPosition().offsetXPt())
                .isLessThan(layout.secondPosition().orElseThrow().offsetXPt());
    }

    @Test
    void rtlSinglePlacedOnRightHalf() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.RTL, dim, SpreadHalf.LEADING);
        assertThat(layout.firstPosition().offsetXPt()).isGreaterThanOrEqualTo(100f);
    }

    @Test
    void ltrSinglePlacedOnLeftHalf() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.LTR, dim, SpreadHalf.LEADING);
        assertThat(layout.firstPosition().offsetXPt()).isLessThan(100f);
    }

    @Test
    void mixedSizesUseMaxBounds() {
        var a = new PageDimension(80f, 100f);
        var b = new PageDimension(120f, 200f);
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, a, b);
        assertThat(layout.spec().widthPt()).isEqualTo(240f);
        assertThat(layout.spec().heightPt()).isEqualTo(200f);
    }

    @Test
    void rtlAndLtrAreMirrored() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout rtl = calc.calculate(ReadingDirection.RTL, dim, dim);
        SpreadLayout ltr = calc.calculate(ReadingDirection.LTR, dim, dim);
        // first RTL = second LTR, and vice versa (for equal-sized pages)
        assertThat(rtl.firstPosition().offsetXPt())
                .isEqualTo(ltr.secondPosition().orElseThrow().offsetXPt(), within(EPS));
        assertThat(rtl.secondPosition().orElseThrow().offsetXPt())
                .isEqualTo(ltr.firstPosition().offsetXPt(), within(EPS));
    }

    @Test
    void rtlTrailingSingleOnLeftHalf() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.RTL, dim, SpreadHalf.TRAILING);
        assertThat(layout.spec().widthPt()).isEqualTo(200f);
        assertThat(layout.firstPosition().offsetXPt()).isZero();
        assertThat(layout.secondPosition()).isEmpty();
    }

    @Test
    void ltrTrailingSingleOnRightHalf() {
        var dim = new PageDimension(100f, 200f);
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.LTR, dim, SpreadHalf.TRAILING);
        assertThat(layout.firstPosition().offsetXPt()).isEqualTo(100f, within(EPS));
        assertThat(layout.secondPosition()).isEmpty();
    }

    @Test
    void leadingAndTrailingSinglesSitOnOppositeHalves() {
        var dim = new PageDimension(100f, 200f);
        var leading = calc.calculateSingle(ReadingDirection.RTL, dim, SpreadHalf.LEADING);
        var trailing = calc.calculateSingle(ReadingDirection.RTL, dim, SpreadHalf.TRAILING);
        assertThat(leading.firstPosition().offsetXPt())
                .isGreaterThan(trailing.firstPosition().offsetXPt());
    }
}
