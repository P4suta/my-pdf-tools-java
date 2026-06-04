package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class LayoutPositionTest {

    @Test
    void offsetsStoredVerbatim() {
        LayoutPosition p = new LayoutPosition(10f, -5f);
        assertThat(p.offsetXPt()).isEqualTo(10f);
        assertThat(p.offsetYPt()).isEqualTo(-5f);
    }

    @Test
    void equalsAndHashCode() {
        LayoutPosition a = new LayoutPosition(10f, -5f);
        LayoutPosition b = new LayoutPosition(10f, -5f);
        LayoutPosition c = new LayoutPosition(10f, 0f);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
