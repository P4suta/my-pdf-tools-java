package io.github.p4suta.register.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RegisterException}'s factory behavior: {@code of} adopts the kind and leaves the
 * detail null, {@code of(kind, cause)} preserves the cause, and {@code withDetail} folds the detail
 * into the throwable message. The kind may be a {@link RegisterErrorKind} or a reused {@link
 * CommonErrorKind}. The exception is presentation-free (no user message).
 */
final class RegisterExceptionTest {

    @Test
    void ofKindLeavesDetailNull() {
        RegisterException ex = RegisterException.of(RegisterErrorKind.IMAGE_UNREADABLE);
        assertThat(ex.kind()).isEqualTo(RegisterErrorKind.IMAGE_UNREADABLE);
        assertThat(ex.technicalDetail()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void ofKindWithCausePreservesCause() {
        var cause = new IllegalStateException("root");
        RegisterException ex = RegisterException.of(RegisterErrorKind.NATIVE_TOOL_FAILED, cause);
        assertThat(ex.kind()).isEqualTo(RegisterErrorKind.NATIVE_TOOL_FAILED);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void withDetailIncludesTechnicalDetailInMessage() {
        RegisterException ex =
                RegisterException.withDetail(
                        RegisterErrorKind.INPUT_NOT_FOUND, "input PDF not found: /x", null);
        assertThat(ex.technicalDetail()).isEqualTo("input PDF not found: /x");
        assertThat(ex.getMessage()).isEqualTo("[INPUT_NOT_FOUND] input PDF not found: /x");
    }

    @Test
    void messageIsJustTheKindWhenDetailAbsent() {
        RegisterException ex = RegisterException.of(RegisterErrorKind.OUTPUT_CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("[OUTPUT_CONFLICT]");
    }

    @Test
    void reusesCommonErrorKind() {
        RegisterException ex = RegisterException.of(CommonErrorKind.INTERNAL);
        assertThat(ex.kind()).isEqualTo(CommonErrorKind.INTERNAL);
        assertThat(ex.getMessage()).isEqualTo("[INTERNAL]");
    }
}
