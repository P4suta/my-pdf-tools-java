package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConversionRequestTest {

    @Test
    void rejectsNonPositiveJobs() {
        assertThatThrownBy(
                        () ->
                                new ConversionRequest(
                                        Direction.RTL,
                                        FirstPage.RIGHT,
                                        true,
                                        true,
                                        true,
                                        true,
                                        false,
                                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jobs must be positive: 0");
    }

    @Test
    void exposesItsOptions() {
        ConversionRequest request =
                new ConversionRequest(
                        Direction.LTR, FirstPage.COVER, false, true, false, true, true, 8);
        assertThat(request.direction()).isEqualTo(Direction.LTR);
        assertThat(request.firstPage()).isEqualTo(FirstPage.COVER);
        assertThat(request.despeckle()).isFalse();
        assertThat(request.register()).isTrue();
        assertThat(request.deskew()).isFalse();
        assertThat(request.scale()).isTrue();
        assertThat(request.pdfA()).isTrue();
        assertThat(request.jobs()).isEqualTo(8);
    }
}
