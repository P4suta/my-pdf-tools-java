package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CacheKeyTest {

    private static final String SHA = "a".repeat(64);

    private static ConversionRequest request(boolean despeckle, int jobs) {
        return new ConversionRequest(
                Direction.RTL, FirstPage.RIGHT, despeckle, true, true, true, false, jobs);
    }

    @Test
    void sameInputAndOptionsProduceTheSameKey() {
        assertThat(CacheKey.of(SHA, request(true, 4)))
                .isEqualTo(CacheKey.of(SHA, request(true, 4)));
    }

    @Test
    void theWorkerCountDoesNotAffectTheKey() {
        assertThat(CacheKey.of(SHA, request(true, 4)))
                .isEqualTo(CacheKey.of(SHA, request(true, 16)));
    }

    @Test
    void aDifferentOptionProducesADifferentKey() {
        assertThat(CacheKey.of(SHA, request(true, 4)))
                .isNotEqualTo(CacheKey.of(SHA, request(false, 4)));
    }

    @Test
    void aDifferentInputProducesADifferentKey() {
        assertThat(CacheKey.of(SHA, request(true, 4)))
                .isNotEqualTo(CacheKey.of("b".repeat(64), request(true, 4)));
    }

    @Test
    void theKeyIsASafeHexToken() {
        assertThat(CacheKey.of(SHA, request(true, 4)).value()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsANonHexValue() {
        assertThatThrownBy(() -> new CacheKey("not-hex"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
