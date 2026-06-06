package io.github.p4suta.tateyokopdf.domain.exception;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import org.jspecify.annotations.Nullable;

/**
 * The single exception type the conversion raises, tagging every failure with an {@link ErrorKind}
 * plus a user-facing message and an optional technical detail. A thin {@link BaseAppException}
 * specialization: the message/detail/cause plumbing and the {@code [NAME] message (detail)}
 * rendering live in the shared base; this subclass narrows the kind to {@link ErrorKind} and
 * re-exposes the {@link #of}/{@link #withDetail} factories returning the concrete type.
 *
 * <p>Unchecked: it propagates to the CLI boundary, where the shared {@code ExceptionMapper} maps
 * the kind to an exit code and log level. Prefer the {@link #of} / {@link #withDetail} factories
 * over the constructor.
 */
public final class SpreadException extends BaseAppException {

    private static final long serialVersionUID = 1L;

    /**
     * @param technicalDetail an optional diagnostic detail
     * @param cause the underlying cause, if any
     */
    public SpreadException(
            ErrorKind kind, @Nullable String technicalDetail, @Nullable Throwable cause) {
        super(kind, technicalDetail, cause);
    }

    /** {@return an exception of {@code kind} with no detail or cause} */
    public static SpreadException of(ErrorKind kind) {
        return new SpreadException(kind, null, null);
    }

    /** {@return an exception of {@code kind} wrapping {@code cause}} */
    public static SpreadException of(ErrorKind kind, Throwable cause) {
        return new SpreadException(kind, null, cause);
    }

    /**
     * {@return an exception of {@code kind} with a diagnostic {@code technicalDetail}, optionally
     * wrapping {@code cause}}
     */
    public static SpreadException withDetail(
            ErrorKind kind, String technicalDetail, @Nullable Throwable cause) {
        return new SpreadException(kind, technicalDetail, cause);
    }

    /**
     * {@return the failure category, narrowed to {@link ErrorKind}} Covariant override of {@link
     * BaseAppException#kind()} so call sites keep an {@link ErrorKind}-typed view.
     */
    @Override
    public ErrorKind kind() {
        return (ErrorKind) super.kind();
    }
}
