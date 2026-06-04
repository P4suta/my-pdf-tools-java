package io.github.p4suta.tateyokopdf.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.p4suta.tateyokopdf.domain.model.LayoutPosition;
import io.github.p4suta.tateyokopdf.domain.model.PageDimension;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import io.github.p4suta.tateyokopdf.domain.model.SpreadHalf;
import io.github.p4suta.tateyokopdf.domain.model.SpreadLayout;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.FloatRange;

final class SpreadLayoutCalculatorProperties {

    private static final float EPS = 0.01f;

    private final SpreadLayoutCalculator calc = new SpreadLayoutCalculator();

    @Provide
    Arbitrary<PageDimension> dims() {
        return Arbitraries.floats()
                .between(1f, 10000f)
                .flatMap(
                        w ->
                                Arbitraries.floats()
                                        .between(1f, 10000f)
                                        .map(h -> new PageDimension(w, h)));
    }

    @Property
    void specWidthIsTwiceMaxWidthForPair(
            @ForAll @From("dims") PageDimension a, @ForAll @From("dims") PageDimension b) {
        SpreadLayout layout = calc.calculate(ReadingDirection.RTL, a, b);
        float expected = Math.max(a.widthPt(), b.widthPt()) * 2f;
        assertThat(layout.spec().widthPt()).isEqualTo(expected, within(EPS));
    }

    @Property
    void specHeightIsMaxHeightForPair(
            @ForAll @From("dims") PageDimension a, @ForAll @From("dims") PageDimension b) {
        SpreadLayout layout = calc.calculate(ReadingDirection.LTR, a, b);
        float expected = Math.max(a.heightPt(), b.heightPt());
        assertThat(layout.spec().heightPt()).isEqualTo(expected, within(EPS));
    }

    @Property
    void specWidthIsTwiceWidthForSingle(@ForAll @From("dims") PageDimension a) {
        SpreadLayout layout = calc.calculateSingle(ReadingDirection.RTL, a, SpreadHalf.LEADING);
        assertThat(layout.spec().widthPt()).isEqualTo(a.widthPt() * 2f, within(EPS));
        assertThat(layout.secondPosition()).isEmpty();
    }

    @Property
    void singleStaysWithinSpreadOnEitherHalf(
            @ForAll ReadingDirection dir,
            @ForAll SpreadHalf half,
            @ForAll @From("dims") PageDimension a) {
        SpreadLayout layout = calc.calculateSingle(dir, a, half);
        LayoutPosition pos = layout.firstPosition();
        assertThat(pos.offsetXPt()).isGreaterThanOrEqualTo(-EPS);
        assertThat(pos.offsetXPt() + a.widthPt())
                .isLessThanOrEqualTo(layout.spec().widthPt() + EPS);
        assertThat(layout.secondPosition()).isEmpty();
    }

    @Property
    void firstPageStaysWithinSpread(
            @ForAll ReadingDirection dir,
            @ForAll @From("dims") PageDimension a,
            @ForAll @From("dims") PageDimension b) {
        SpreadLayout layout = calc.calculate(dir, a, b);
        LayoutPosition first = layout.firstPosition();
        assertThat(first.offsetXPt()).isGreaterThanOrEqualTo(-EPS);
        assertThat(first.offsetXPt() + a.widthPt())
                .isLessThanOrEqualTo(layout.spec().widthPt() + EPS);
        assertThat(first.offsetYPt()).isGreaterThanOrEqualTo(-EPS);
        assertThat(first.offsetYPt() + a.heightPt())
                .isLessThanOrEqualTo(layout.spec().heightPt() + EPS);
    }

    @Property
    void secondPageStaysWithinSpread(
            @ForAll ReadingDirection dir,
            @ForAll @From("dims") PageDimension a,
            @ForAll @From("dims") PageDimension b) {
        SpreadLayout layout = calc.calculate(dir, a, b);
        LayoutPosition second = layout.secondPosition().orElseThrow();
        assertThat(second.offsetXPt()).isGreaterThanOrEqualTo(-EPS);
        assertThat(second.offsetXPt() + b.widthPt())
                .isLessThanOrEqualTo(layout.spec().widthPt() + EPS);
        assertThat(second.offsetYPt()).isGreaterThanOrEqualTo(-EPS);
        assertThat(second.offsetYPt() + b.heightPt())
                .isLessThanOrEqualTo(layout.spec().heightPt() + EPS);
    }

    @Property
    void pagesVerticallyCentered(
            @ForAll ReadingDirection dir,
            @ForAll @From("dims") PageDimension a,
            @ForAll @From("dims") PageDimension b) {
        SpreadLayout layout = calc.calculate(dir, a, b);
        float specH = layout.spec().heightPt();
        assertThat(layout.firstPosition().offsetYPt())
                .isEqualTo((specH - a.heightPt()) / 2f, within(EPS));
        assertThat(layout.secondPosition().orElseThrow().offsetYPt())
                .isEqualTo((specH - b.heightPt()) / 2f, within(EPS));
    }

    @Property
    void rtlAndLtrAreMirroredForEqualSizedPair(
            @ForAll @From("dims") PageDimension dim,
            @ForAll @FloatRange(min = 0.1f, max = 1.5f) float scale) {
        var second = new PageDimension(dim.widthPt() * scale, dim.heightPt() * scale);
        SpreadLayout rtl = calc.calculate(ReadingDirection.RTL, dim, second);
        SpreadLayout ltr = calc.calculate(ReadingDirection.LTR, dim, second);
        float spreadW = rtl.spec().widthPt();
        // RTL.firstX + LTR.firstX should sum to spread - dim.width (mirror around center)
        assertThat(rtl.firstPosition().offsetXPt() + ltr.firstPosition().offsetXPt())
                .isEqualTo(spreadW - dim.widthPt(), within(EPS));
        assertThat(
                        rtl.secondPosition().orElseThrow().offsetXPt()
                                + ltr.secondPosition().orElseThrow().offsetXPt())
                .isEqualTo(spreadW - second.widthPt(), within(EPS));
    }
}
