package io.github.p4suta.shared.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link BaseAppException} through a concrete test subclass that mirrors the {@code
 * of(kind)} / {@code of(kind, cause)} / {@code withDetail(kind, detail, cause)} factory shape each
 * app's domain exception exposes over the protected constructor. The base is presentation-free: it
 * carries a kind and an optional technical detail; the throwable message is a developer string
 * ({@code [NAME] detail}), never user-facing prose.
 */
final class BaseAppExceptionTest {

    /** A minimal concrete {@link BaseAppException} standing in for an app's domain exception. */
    private static final class SampleException extends BaseAppException {

        private static final long serialVersionUID = 1L;

        private SampleException(
                ErrorCategory kind, @Nullable String technicalDetail, @Nullable Throwable cause) {
            super(kind, technicalDetail, cause);
        }

        static SampleException of(ErrorCategory kind) {
            return new SampleException(kind, null, null);
        }

        static SampleException of(ErrorCategory kind, Throwable cause) {
            return new SampleException(kind, null, cause);
        }

        static SampleException withDetail(
                ErrorCategory kind, String technicalDetail, @Nullable Throwable cause) {
            return new SampleException(kind, technicalDetail, cause);
        }
    }

    @Test
    void ofExposesKindWithNoDetailOrCause() {
        SampleException ex = SampleException.of(CommonErrorKind.INTERNAL);
        assertThat(ex.kind()).isEqualTo(CommonErrorKind.INTERNAL);
        assertThat(ex.technicalDetail()).isNull();
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void ofWithCauseWrapsTheCause() {
        Throwable cause = new IllegalStateException("boom");
        SampleException ex = SampleException.of(CommonErrorKind.OUT_OF_MEMORY, cause);
        assertThat(ex.kind()).isEqualTo(CommonErrorKind.OUT_OF_MEMORY);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.technicalDetail()).isNull();
    }

    @Test
    void withDetailKeepsDetailAndPutsItInTheThrowableMessage() {
        SampleException ex =
                SampleException.withDetail(CommonErrorKind.INVALID_PARAMETER, "dpi=-1", null);
        assertThat(ex.technicalDetail()).isEqualTo("dpi=-1");
        assertThat(ex.getMessage()).isEqualTo("[INVALID_PARAMETER] dpi=-1");
    }

    @Test
    void throwableMessageIsJustTheKindWhenNoDetail() {
        SampleException ex = SampleException.of(CommonErrorKind.INTERNAL);
        assertThat(ex.getMessage()).isEqualTo("[INTERNAL]");
    }

    @Test
    void throwableMessageRendersForEveryCommonKind() {
        for (CommonErrorKind kind : CommonErrorKind.values()) {
            SampleException ex = SampleException.withDetail(kind, "detail", null);
            assertThat(ex.getMessage()).isEqualTo("[" + kind.name() + "] detail");
        }
    }
}
