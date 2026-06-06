package io.github.p4suta.register.domain.exception;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import org.jspecify.annotations.Nullable;

/**
 * The register domain exception: an unchecked failure tagging every error with an {@link
 * ErrorCategory} (a {@link RegisterErrorKind} or a {@link
 * io.github.p4suta.shared.kernel.error.CommonErrorKind}) plus an optional technical detail. Extends
 * the shared {@link BaseAppException}; presentation-free (surfaces localize from the kind).
 *
 * <p>Unchecked: it propagates to the CLI boundary, where the shared {@code ExceptionMapper} turns
 * the kind into an exit code and log level. Prefer the {@link #of} / {@link #withDetail} factories
 * over the constructor.
 */
public final class RegisterException extends BaseAppException {

    private static final long serialVersionUID = 1L;

    private RegisterException(
            ErrorCategory kind, @Nullable String technicalDetail, @Nullable Throwable cause) {
        super(kind, technicalDetail, cause);
    }

    /** {@return an exception of {@code kind} with no detail or cause} */
    public static RegisterException of(ErrorCategory kind) {
        return new RegisterException(kind, null, null);
    }

    /** {@return an exception of {@code kind} wrapping {@code cause}} */
    public static RegisterException of(ErrorCategory kind, Throwable cause) {
        return new RegisterException(kind, null, cause);
    }

    /**
     * {@return an exception of {@code kind} with a diagnostic {@code technicalDetail}, optionally
     * wrapping {@code cause}}
     */
    public static RegisterException withDetail(
            ErrorCategory kind, String technicalDetail, @Nullable Throwable cause) {
        return new RegisterException(kind, technicalDetail, cause);
    }
}
