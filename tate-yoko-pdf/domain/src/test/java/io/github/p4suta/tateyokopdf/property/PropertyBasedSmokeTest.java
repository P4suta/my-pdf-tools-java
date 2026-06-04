package io.github.p4suta.tateyokopdf.property;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** Tiny smoke test to confirm jqwik runs under JUnit Jupiter 6.x. */
final class PropertyBasedSmokeTest {

    @Property
    void integerAdditionIsCommutative(
            @ForAll @IntRange(min = -1000, max = 1000) int a,
            @ForAll @IntRange(min = -1000, max = 1000) int b) {
        assertThat(a + b).isEqualTo(b + a);
    }
}
