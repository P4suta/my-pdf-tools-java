package io.github.p4suta.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

/**
 * Table-driven cases pin exit code + slf4j level + client-fault per {@link CommonErrorKind}; the
 * fallback cases pin the throwable&rarr;kind table. The mapping is presentation-free — it carries
 * no user message — so the only string asserted is the (path-masked) technical detail.
 */
final class ExceptionMapperTest {

    /** A concrete {@link BaseAppException} fixture; carries its own kind through the mapper. */
    private static final class FakeAppException extends BaseAppException {
        private static final long serialVersionUID = 1L;

        FakeAppException(ErrorCategory kind, @Nullable String technicalDetail) {
            super(kind, technicalDetail, null);
        }
    }

    /** An app-specific {@link ErrorCategory} not in {@link CommonErrorKind}, for the extra-rule. */
    private enum AppKind implements ErrorCategory {
        IMAGE_UNREADABLE(65, Severity.WARN, true),
        // No shared kind uses Severity.INFO; this fixture exercises the INFO -> Level.INFO arm.
        INFO_NOTICE(0, Severity.INFO, false);

        private final int exitCode;
        private final Severity severity;
        private final boolean clientFault;

        AppKind(int exitCode, Severity severity, boolean clientFault) {
            this.exitCode = exitCode;
            this.severity = severity;
            this.clientFault = clientFault;
        }

        @Override
        public boolean isClientFault() {
            return clientFault;
        }

        @Override
        public int exitCode() {
            return exitCode;
        }

        @Override
        public Severity severity() {
            return severity;
        }
    }

    // every CommonErrorKind, carried by a domain exception, maps to its own row

    static Stream<Arguments> commonKindRows() {
        // kind, expected exitCode, expected level, expected clientFault
        return Stream.of(
                Arguments.of(CommonErrorKind.INVALID_PARAMETER, 64, Level.WARN, true),
                Arguments.of(CommonErrorKind.OUTPUT_CONFLICT, 73, Level.WARN, true),
                Arguments.of(CommonErrorKind.OUT_OF_MEMORY, 137, Level.ERROR, false),
                Arguments.of(CommonErrorKind.INTERNAL, 70, Level.ERROR, false));
    }

    @ParameterizedTest
    @MethodSource("commonKindRows")
    void domainExceptionMapsExactlyPerSpec(
            CommonErrorKind kind,
            int expectedExit,
            Level expectedLevel,
            boolean expectedClientFault) {
        var mapping = ExceptionMapper.map(new FakeAppException(kind, null));

        assertThat(mapping.kind()).isEqualTo(kind);
        assertThat(mapping.exitCode()).isEqualTo(expectedExit);
        assertThat(mapping.level()).isEqualTo(expectedLevel);
        assertThat(mapping.kind().isClientFault()).isEqualTo(expectedClientFault);
    }

    /** The mapping reads exitCode()/severity() straight off the ErrorCategory, for every kind. */
    @ParameterizedTest
    @EnumSource(CommonErrorKind.class)
    void mappingReadsExitCodeAndSeverityOffTheCategory(CommonErrorKind kind) {
        var mapping = ExceptionMapper.map(new FakeAppException(kind, null));
        assertThat(mapping.exitCode()).isEqualTo(kind.exitCode());
        Level expected =
                switch (kind.severity()) {
                    case INFO -> Level.INFO;
                    case WARN -> Level.WARN;
                    case ERROR -> Level.ERROR;
                };
        assertThat(mapping.level()).isEqualTo(expected);
    }

    @Test
    void infoSeverityTranslatesToSlf4jInfoLevel() {
        var mapping = ExceptionMapper.map(new FakeAppException(AppKind.INFO_NOTICE, null));
        assertThat(mapping.level()).isEqualTo(Level.INFO);
    }

    // throwable -> kind fallback (first match wins)

    @Test
    void illegalArgumentFallsBackToInvalidParameter() {
        var mapping = ExceptionMapper.map(new IllegalArgumentException("bad value"));
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.INVALID_PARAMETER);
        assertThat(mapping.exitCode()).isEqualTo(64);
        assertThat(mapping.level()).isEqualTo(Level.WARN);
        assertThat(mapping.technicalDetail()).isEqualTo("bad value");
    }

    @Test
    void illegalArgumentWithNullMessageUsesClassNameAsDetail() {
        var mapping = ExceptionMapper.map(new IllegalArgumentException());
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.INVALID_PARAMETER);
        assertThat(mapping.technicalDetail()).isEqualTo("IllegalArgumentException");
    }

    @Test
    void outOfMemoryFallsBackToOutOfMemoryKind() {
        var mapping = ExceptionMapper.map(new OutOfMemoryError("heap"));
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.OUT_OF_MEMORY);
        assertThat(mapping.exitCode()).isEqualTo(137);
        assertThat(mapping.level()).isEqualTo(Level.ERROR);
    }

    @Test
    void ioExceptionFallsBackToInternal() {
        var mapping = ExceptionMapper.map(new IOException("disk"));
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.INTERNAL);
        assertThat(mapping.exitCode()).isEqualTo(70);
        assertThat(mapping.level()).isEqualTo(Level.ERROR);
    }

    @Test
    void arbitraryThrowableFallsBackToInternal() {
        var mapping = ExceptionMapper.map(new RuntimeException("boom"));
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.INTERNAL);
        assertThat(mapping.exitCode()).isEqualTo(70);
    }

    // domain exception always wins over class-based fallback

    @Test
    void domainExceptionKindWinsEvenWhenItIsAnIllegalArgumentSubclass() {
        // A FakeAppException is a RuntimeException; its IMAGE_UNREADABLE kind must survive, not be
        // re-derived to INTERNAL by the fallback.
        var mapping = ExceptionMapper.map(new FakeAppException(AppKind.IMAGE_UNREADABLE, "x"));
        assertThat(mapping.kind()).isEqualTo(AppKind.IMAGE_UNREADABLE);
        assertThat(mapping.exitCode()).isEqualTo(65);
        assertThat(mapping.level()).isEqualTo(Level.WARN);
        assertThat(mapping.technicalDetail()).isEqualTo("x");
    }

    // extra app-supplied rule: consulted before the baseline, but not before a domain exception

    @Test
    void extraRuleClassifiesAThrowableTheBaselineWouldMisroute() {
        // A bare UncheckedIOException would hit the "else -> INTERNAL" baseline; an app rule can
        // promote it to a more specific kind.
        var mapping =
                ExceptionMapper.map(
                        new UncheckedIOException(new IOException("nope")),
                        t -> t instanceof UncheckedIOException ? AppKind.IMAGE_UNREADABLE : null);
        assertThat(mapping.kind()).isEqualTo(AppKind.IMAGE_UNREADABLE);
        assertThat(mapping.exitCode()).isEqualTo(65);
    }

    @Test
    void extraRuleReturningNullFallsThroughToBaseline() {
        var mapping = ExceptionMapper.map(new IOException("io"), t -> null);
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.INTERNAL);
        assertThat(mapping.exitCode()).isEqualTo(70);
    }

    @Test
    void domainExceptionShortCircuitsBeforeExtraRule() {
        var ex = new FakeAppException(CommonErrorKind.INTERNAL, null);
        var mapping = ExceptionMapper.map(ex, t -> AppKind.IMAGE_UNREADABLE);
        assertThat(mapping.kind()).isEqualTo(CommonErrorKind.INTERNAL);
    }

    // PII masking of the technical detail

    @Test
    void maskAbsolutePathsInTechnicalDetail() {
        var ex =
                new FakeAppException(
                        CommonErrorKind.INTERNAL, "Failed to load /tmp/secret/path/to/file.pdf");
        var mapping = ExceptionMapper.map(ex);
        assertThat(mapping.technicalDetail()).doesNotContain("/tmp/secret").contains("<path>");
    }

    @Test
    void nullTechnicalDetailStaysNull() {
        var mapping = ExceptionMapper.map(new FakeAppException(CommonErrorKind.INTERNAL, null));
        assertThat(mapping.technicalDetail()).isNull();
    }
}
