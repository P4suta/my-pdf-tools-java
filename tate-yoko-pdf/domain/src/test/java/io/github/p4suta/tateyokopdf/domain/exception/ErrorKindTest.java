package io.github.p4suta.tateyokopdf.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins {@link ErrorKind}'s exit code and the clientFault &hArr; WARN invariant. The category is
 * presentation-free; user text lives in each surface's catalog, not on the constant.
 */
final class ErrorKindTest {

    @Test
    void pdfWriteFailedCarriesItsExitCode() {
        ErrorKind k = ErrorKind.PDF_WRITE_FAILED;
        assertThat(k.exitCode()).isEqualTo(73);
        assertThat(k.severity()).isEqualTo(Severity.ERROR);
        assertThat(k.name()).isEqualTo("PDF_WRITE_FAILED");
    }

    /**
     * Invariant: a client fault logs at WARN, an internal/environmental one at ERROR; never INFO.
     */
    @ParameterizedTest
    @EnumSource(ErrorKind.class)
    void severityTracksClientFault(ErrorKind k) {
        assertThat(k.severity()).isNotEqualTo(Severity.INFO);
        if (k.isClientFault()) {
            assertThat(k.severity()).isEqualTo(Severity.WARN);
        } else {
            assertThat(k.severity()).isEqualTo(Severity.ERROR);
        }
    }
}
