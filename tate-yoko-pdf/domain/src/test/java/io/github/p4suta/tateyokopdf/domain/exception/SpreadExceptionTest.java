package io.github.p4suta.tateyokopdf.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class SpreadExceptionTest {

    @Test
    void ofKindLeavesDetailNull() {
        var ex = SpreadException.of(ErrorKind.PDF_CORRUPTED);
        assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_CORRUPTED);
        assertThat(ex.technicalDetail()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void ofKindWithCausePreservesCause() {
        var cause = new IllegalStateException("root");
        var ex = SpreadException.of(ErrorKind.PDF_WRITE_FAILED, cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void withDetailIncludesTechnicalDetailInMessage() {
        var ex = SpreadException.withDetail(ErrorKind.PDF_NOT_FOUND, "path=/x", null);
        assertThat(ex.technicalDetail()).isEqualTo("path=/x");
        assertThat(ex.getMessage()).contains("PDF_NOT_FOUND").contains("path=/x");
    }

    @Test
    void messageOmitsTechnicalDetailWhenAbsent() {
        var ex = SpreadException.of(ErrorKind.PDF_PASSWORD_PROTECTED);
        assertThat(ex.getMessage()).contains("PDF_PASSWORD_PROTECTED").doesNotContain("(");
    }
}
