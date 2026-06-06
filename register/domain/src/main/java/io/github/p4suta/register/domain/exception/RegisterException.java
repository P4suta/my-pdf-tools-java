package io.github.p4suta.register.domain.exception;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import org.jspecify.annotations.Nullable;

/**
 * The register domain exception: an unchecked failure tagging every error with an {@link
 * ErrorCategory} (a {@link RegisterErrorKind} or a {@link
 * io.github.p4suta.shared.kernel.error.CommonErrorKind}) plus a user-facing message and an optional
 * technical detail. Extends the shared {@link BaseAppException}.
 *
 * <p>Unchecked: it propagates to the CLI boundary, where the shared {@code ExceptionMapper} turns
 * the kind into an exit code and log level. Prefer the {@link #of} / {@link #withDetail} factories
 * over the constructor.
 */
public final class RegisterException extends BaseAppException {

    private static final long serialVersionUID = 1L;

    private RegisterException(
            ErrorCategory kind,
            String userMessage,
            @Nullable String technicalDetail,
            @Nullable Throwable cause) {
        super(kind, userMessage, technicalDetail, cause);
    }

    /** {@return an exception of {@code kind} with its default user message and no cause} */
    public static RegisterException of(ErrorCategory kind) {
        return new RegisterException(kind, kind.defaultUserMessage(), null, null);
    }

    /**
     * {@return an exception of {@code kind} with its default user message, wrapping {@code cause}}
     */
    public static RegisterException of(ErrorCategory kind, Throwable cause) {
        return new RegisterException(kind, kind.defaultUserMessage(), null, cause);
    }

    /**
     * {@return an exception of {@code kind} with its default user message plus a diagnostic {@code
     * technicalDetail}, optionally wrapping {@code cause}}
     */
    public static RegisterException withDetail(
            ErrorCategory kind, String technicalDetail, @Nullable Throwable cause) {
        return new RegisterException(kind, kind.defaultUserMessage(), technicalDetail, cause);
    }
}
