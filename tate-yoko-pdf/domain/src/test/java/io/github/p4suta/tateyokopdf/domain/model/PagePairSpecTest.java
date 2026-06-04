package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import org.junit.jupiter.api.Test;

final class PagePairSpecTest {

    @Test
    void pairValidIndices() {
        var p = new PagePairSpec.Pair(0, 1);
        assertThat(p.firstIndex()).isZero();
        assertThat(p.secondIndex()).isOne();
    }

    @Test
    void pairAllowsSameIndex() {
        // Same index is unusual but not invalid at the value-object layer.
        var p = new PagePairSpec.Pair(3, 3);
        assertThat(p.firstIndex()).isEqualTo(p.secondIndex());
    }

    @Test
    void pairRejectsNegativeFirst() {
        assertThatThrownBy(() -> new PagePairSpec.Pair(-1, 0))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_INVALID_PAGE));
    }

    @Test
    void pairRejectsNegativeSecond() {
        assertThatThrownBy(() -> new PagePairSpec.Pair(0, -1)).isInstanceOf(SpreadException.class);
    }

    @Test
    void singleValid() {
        var s = new PagePairSpec.Single(5);
        assertThat(s.pageIndex()).isEqualTo(5);
    }

    @Test
    void singleRejectsNegative() {
        assertThatThrownBy(() -> new PagePairSpec.Single(-1))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_INVALID_PAGE));
    }

    @Test
    void pairEqualsAndHashCode() {
        var a = new PagePairSpec.Pair(0, 1);
        var b = new PagePairSpec.Pair(0, 1);
        var c = new PagePairSpec.Pair(0, 2);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void singleEqualsAndHashCode() {
        var a = new PagePairSpec.Single(5);
        var b = new PagePairSpec.Single(5);
        var c = new PagePairSpec.Single(6);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
