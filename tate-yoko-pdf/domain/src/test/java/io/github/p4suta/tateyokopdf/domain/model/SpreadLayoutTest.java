package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SpreadLayoutTest {

    @Test
    void exposesSpecAndPositions() {
        var spec = new SpreadSpec(400f, 200f);
        var first = new LayoutPosition(0f, 0f);
        var second = new LayoutPosition(200f, 0f);
        var layout = new SpreadLayout(spec, first, Optional.of(second));
        assertThat(layout.spec()).isEqualTo(spec);
        assertThat(layout.firstPosition()).isEqualTo(first);
        assertThat(layout.secondPosition()).contains(second);
    }

    @Test
    void singlePageHasEmptySecondPosition() {
        var spec = new SpreadSpec(400f, 200f);
        var first = new LayoutPosition(100f, 0f);
        var layout = new SpreadLayout(spec, first, Optional.empty());
        assertThat(layout.secondPosition()).isEmpty();
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullSpec() {
        assertThatThrownBy(
                        () -> new SpreadLayout(null, new LayoutPosition(0f, 0f), Optional.empty()))
                .isInstanceOf(SpreadException.class);
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullFirstPosition() {
        assertThatThrownBy(() -> new SpreadLayout(new SpreadSpec(1f, 1f), null, Optional.empty()))
                .isInstanceOf(SpreadException.class);
    }

    @SuppressWarnings("NullAway")
    @Test
    void rejectsNullSecondPositionOptional() {
        assertThatThrownBy(
                        () ->
                                new SpreadLayout(
                                        new SpreadSpec(1f, 1f), new LayoutPosition(0f, 0f), null))
                .isInstanceOf(SpreadException.class);
    }

    @Test
    void equalsAndHashCode() {
        var spec = new SpreadSpec(400f, 200f);
        var first = new LayoutPosition(0f, 0f);
        var second = new LayoutPosition(200f, 0f);
        var a = new SpreadLayout(spec, first, Optional.of(second));
        var b = new SpreadLayout(spec, first, Optional.of(second));
        var c = new SpreadLayout(spec, first, Optional.empty());
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
