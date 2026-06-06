package io.github.p4suta.despeckle.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link DespeckleException}'s {@code of(kind)} / {@code of(kind, cause)} / {@code
 * withDetail(kind, detail, cause)} factories over the shared {@link BaseAppException} base. Works
 * with both an app kind ({@link DespeckleErrorKind}) and a reused {@link CommonErrorKind}. The
 * exception is presentation-free: it carries a kind and an optional technical detail only.
 */
final class DespeckleExceptionTest {

    @Test
    void ofExposesKindWithNoDetailOrCause() {
        DespeckleException ex = DespeckleException.of(DespeckleErrorKind.IMAGE_UNREADABLE);
        assertThat(ex.kind()).isEqualTo(DespeckleErrorKind.IMAGE_UNREADABLE);
        assertThat(ex.technicalDetail()).isNull();
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(BaseAppException.class).isInstanceOf(RuntimeException.class);
    }

    @Test
    void ofWithCauseWrapsTheCause() {
        Throwable cause = new IllegalStateException("boom");
        DespeckleException ex = DespeckleException.of(DespeckleErrorKind.NATIVE_TOOL_FAILED, cause);
        assertThat(ex.kind()).isEqualTo(DespeckleErrorKind.NATIVE_TOOL_FAILED);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.technicalDetail()).isNull();
    }

    @Test
    void withDetailKeepsDetailAndPutsItInTheThrowableMessage() {
        DespeckleException ex =
                DespeckleException.withDetail(
                        DespeckleErrorKind.OUTPUT_CONFLICT, "out.pdf already exists", null);
        assertThat(ex.technicalDetail()).isEqualTo("out.pdf already exists");
        assertThat(ex.getMessage()).isEqualTo("[OUTPUT_CONFLICT] out.pdf already exists");
    }

    @Test
    void throwableMessageIsJustTheKindWhenNoDetail() {
        DespeckleException ex = DespeckleException.of(DespeckleErrorKind.INPUT_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("[INPUT_NOT_FOUND]");
    }

    @Test
    void reusesACommonErrorKindUnchanged() {
        DespeckleException ex =
                DespeckleException.withDetail(
                        CommonErrorKind.INVALID_PARAMETER, "dpi=-1", new RuntimeException("x"));
        assertThat(ex.kind()).isEqualTo(CommonErrorKind.INVALID_PARAMETER);
        assertThat(ex.getMessage()).isEqualTo("[INVALID_PARAMETER] dpi=-1");
    }
}
