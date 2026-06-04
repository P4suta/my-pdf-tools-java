package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import org.junit.jupiter.api.Test;

final class SpreadSpecTest {

    @Test
    void positiveDimensionsAccepted() {
        SpreadSpec s = new SpreadSpec(1190f, 842f);
        assertThat(s.widthPt()).isEqualTo(1190f);
        assertThat(s.heightPt()).isEqualTo(842f);
    }

    @Test
    void zeroWidthRejected() {
        assertThatThrownBy(() -> new SpreadSpec(0f, 100f))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.INVALID_PARAMETER));
    }

    @Test
    void negativeHeightRejected() {
        assertThatThrownBy(() -> new SpreadSpec(100f, -1f)).isInstanceOf(SpreadException.class);
    }

    @Test
    void equalsAndHashCode() {
        SpreadSpec a = new SpreadSpec(400f, 200f);
        SpreadSpec b = new SpreadSpec(400f, 200f);
        SpreadSpec c = new SpreadSpec(400f, 201f);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
