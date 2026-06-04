package io.github.p4suta.tateyokopdf.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class PdfVersionTest {

    @Test
    void pdf17ExposesHeaderValueAndLabel() {
        assertThat(PdfVersion.PDF_1_7.headerValue()).isEqualTo(1.7f);
        assertThat(PdfVersion.PDF_1_7.label()).isEqualTo("1.7");
    }

    @Test
    void pdf20ExposesHeaderValueAndLabel() {
        assertThat(PdfVersion.PDF_2_0.headerValue()).isEqualTo(2.0f);
        assertThat(PdfVersion.PDF_2_0.label()).isEqualTo("2.0");
    }
}
