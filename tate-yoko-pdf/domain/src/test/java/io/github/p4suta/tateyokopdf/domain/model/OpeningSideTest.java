package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class OpeningSideTest {

    @Test
    void rtlLeadsOnRight() {
        assertThat(OpeningSide.leadingFor(ReadingDirection.RTL)).isEqualTo(OpeningSide.RIGHT);
    }

    @Test
    void ltrLeadsOnLeft() {
        assertThat(OpeningSide.leadingFor(ReadingDirection.LTR)).isEqualTo(OpeningSide.LEFT);
    }
}
