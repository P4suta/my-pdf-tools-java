package io.github.p4suta.register.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins {@link RegisterErrorKind}'s exit code, severity and client-fault flag per constant, plus the
 * invariant clientFault &hArr; WARN. The category is presentation-free; user text lives in each
 * surface's catalog, not here.
 */
final class RegisterErrorKindTest {

    @Test
    void inputNotFoundMatchesSpec() {
        RegisterErrorKind k = RegisterErrorKind.INPUT_NOT_FOUND;
        assertThat(k.exitCode()).isEqualTo(66);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.name()).isEqualTo("INPUT_NOT_FOUND");
    }

    @Test
    void imageUnreadableMatchesSpec() {
        RegisterErrorKind k = RegisterErrorKind.IMAGE_UNREADABLE;
        assertThat(k.exitCode()).isEqualTo(65);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.name()).isEqualTo("IMAGE_UNREADABLE");
    }

    @Test
    void outputConflictMatchesSpec() {
        RegisterErrorKind k = RegisterErrorKind.OUTPUT_CONFLICT;
        assertThat(k.exitCode()).isEqualTo(73);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.name()).isEqualTo("OUTPUT_CONFLICT");
    }

    @Test
    void nativeToolFailedMatchesSpec() {
        RegisterErrorKind k = RegisterErrorKind.NATIVE_TOOL_FAILED;
        assertThat(k.exitCode()).isEqualTo(70);
        assertThat(k.severity()).isEqualTo(Severity.ERROR);
        assertThat(k.isClientFault()).isFalse();
        assertThat(k.name()).isEqualTo("NATIVE_TOOL_FAILED");
    }

    /**
     * Invariant: {@code isClientFault() == true} ⟹ {@code severity() == WARN}, {@code
     * isClientFault() == false} ⟹ {@code severity() == ERROR}. No constant may use INFO.
     */
    @ParameterizedTest
    @EnumSource(RegisterErrorKind.class)
    void severityTracksClientFault(RegisterErrorKind k) {
        assertThat(k.severity()).isNotEqualTo(Severity.INFO);
        if (k.isClientFault()) {
            assertThat(k.severity()).isEqualTo(Severity.WARN);
        } else {
            assertThat(k.severity()).isEqualTo(Severity.ERROR);
        }
    }
}
