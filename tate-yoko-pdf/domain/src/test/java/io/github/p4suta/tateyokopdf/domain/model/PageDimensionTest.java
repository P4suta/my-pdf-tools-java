package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import org.junit.jupiter.api.Test;

final class PageDimensionTest {

    @Test
    void validDimensionsAccepted() {
        PageDimension d = new PageDimension(595f, 842f);
        assertThat(d.widthPt()).isEqualTo(595f);
        assertThat(d.heightPt()).isEqualTo(842f);
    }

    @Test
    void subPixelDimensionsAccepted() {
        PageDimension d = new PageDimension(0.5f, 0.5f);
        assertThat(d.widthPt()).isEqualTo(0.5f);
    }

    @Test
    void zeroWidthRejected() {
        assertThatThrownBy(() -> new PageDimension(0f, 100f))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.INVALID_PARAMETER));
    }

    @Test
    void negativeWidthRejected() {
        assertThatThrownBy(() -> new PageDimension(-1f, 100f)).isInstanceOf(SpreadException.class);
    }

    @Test
    void zeroHeightRejected() {
        assertThatThrownBy(() -> new PageDimension(100f, 0f)).isInstanceOf(SpreadException.class);
    }

    @Test
    void negativeHeightRejected() {
        assertThatThrownBy(() -> new PageDimension(100f, -1f)).isInstanceOf(SpreadException.class);
    }

    @Test
    void maxTakesLargerOfEach() {
        PageDimension a = new PageDimension(100f, 200f);
        PageDimension b = new PageDimension(150f, 180f);
        PageDimension m = PageDimension.max(a, b);
        assertThat(m.widthPt()).isEqualTo(150f);
        assertThat(m.heightPt()).isEqualTo(200f);
    }

    @Test
    void maxIsCommutative() {
        PageDimension a = new PageDimension(100f, 200f);
        PageDimension b = new PageDimension(150f, 180f);
        assertThat(PageDimension.max(a, b)).isEqualTo(PageDimension.max(b, a));
    }

    @Test
    void maxIsIdempotent() {
        PageDimension a = new PageDimension(100f, 200f);
        assertThat(PageDimension.max(a, a)).isEqualTo(a);
    }

    @Test
    void equalsAndHashCodeFollowRecordSemantics() {
        PageDimension a = new PageDimension(100f, 200f);
        PageDimension b = new PageDimension(100f, 200f);
        PageDimension c = new PageDimension(100f, 201f);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo("not a PageDimension");
        assertThat(a.toString()).contains("100").contains("200");
    }
}
