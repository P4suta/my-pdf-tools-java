package io.github.p4suta.shared.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link BaseAppException} through a concrete test subclass that mirrors the {@code
 * of(kind)} / {@code of(kind, cause)} / {@code withDetail(kind, detail, cause)} factory shape each
 * app's domain exception (tate's {@code SpreadException} etc.) exposes over the protected
 * constructor. The base is abstract, so the factories live on the subclass.
 */
final class BaseAppExceptionTest {

    /** A minimal concrete {@link BaseAppException} standing in for an app's domain exception. */
    private static final class SampleException extends BaseAppException {

        private static final long serialVersionUID = 1L;

        private SampleException(
                ErrorCategory kind,
                String userMessage,
                @Nullable String technicalDetail,
                @Nullable Throwable cause) {
            super(kind, userMessage, technicalDetail, cause);
        }

        static SampleException of(ErrorCategory kind) {
            return new SampleException(kind, kind.defaultUserMessage(), null, null);
        }

        static SampleException of(ErrorCategory kind, Throwable cause) {
            return new SampleException(kind, kind.defaultUserMessage(), null, cause);
        }

        static SampleException withDetail(
                ErrorCategory kind, String technicalDetail, @Nullable Throwable cause) {
            return new SampleException(kind, kind.defaultUserMessage(), technicalDetail, cause);
        }
    }

    @Test
    void ofExposesKindAndDefaultMessageWithNoDetailOrCause() {
        SampleException ex = SampleException.of(CommonErrorKind.INTERNAL);
        assertThat(ex.kind()).isEqualTo(CommonErrorKind.INTERNAL);
        assertThat(ex.userMessage()).isEqualTo(CommonErrorKind.INTERNAL.defaultUserMessage());
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
    void withDetailKeepsDetailOutOfTheUserMessageButInTheThrowableMessage() {
        SampleException ex =
                SampleException.withDetail(CommonErrorKind.INVALID_PARAMETER, "dpi=-1", null);
        assertThat(ex.userMessage())
                .isEqualTo(CommonErrorKind.INVALID_PARAMETER.defaultUserMessage());
        assertThat(ex.technicalDetail()).isEqualTo("dpi=-1");
        assertThat(ex.getMessage()).isEqualTo("[INVALID_PARAMETER] 入力値が不正です。 (dpi=-1)");
    }

    @Test
    void throwableMessageOmitsParensWhenNoDetail() {
        SampleException ex = SampleException.of(CommonErrorKind.INTERNAL);
        assertThat(ex.getMessage()).isEqualTo("[INTERNAL] 予期しないエラーが発生しました。");
    }

    @Test
    void withDetailRendersDetailedThrowableMessageForEveryCommonKind() {
        for (CommonErrorKind kind : CommonErrorKind.values()) {
            SampleException ex = SampleException.withDetail(kind, "detail", null);
            assertThat(ex.getMessage())
                    .isEqualTo("[" + kind.name() + "] " + kind.defaultUserMessage() + " (detail)");
        }
    }
}
