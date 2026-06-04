package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class FirstPageModeTest {

    @Test
    void leadingSideOpensStandard() {
        // The reading direction's leading side (RTL→right, LTR→left) opens normally.
        assertThat(FirstPageMode.fromSide(OpeningSide.RIGHT, ReadingDirection.RTL))
                .isEqualTo(FirstPageMode.STANDARD);
        assertThat(FirstPageMode.fromSide(OpeningSide.LEFT, ReadingDirection.LTR))
                .isEqualTo(FirstPageMode.STANDARD);
    }

    @Test
    void trailingSideLeadsWithBlank() {
        // The opposite side puts page 1 across from an implied blank.
        assertThat(FirstPageMode.fromSide(OpeningSide.LEFT, ReadingDirection.RTL))
                .isEqualTo(FirstPageMode.LEADING_BLANK);
        assertThat(FirstPageMode.fromSide(OpeningSide.RIGHT, ReadingDirection.LTR))
                .isEqualTo(FirstPageMode.LEADING_BLANK);
    }
}
