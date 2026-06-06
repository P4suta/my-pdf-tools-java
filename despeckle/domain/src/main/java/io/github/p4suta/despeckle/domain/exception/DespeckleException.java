package io.github.p4suta.despeckle.domain.exception;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import org.jspecify.annotations.Nullable;

/**
 * The despeckle domain exception: tags every failure the tool raises with an {@link ErrorCategory}
 * (a {@link DespeckleErrorKind} or a {@code CommonErrorKind}) plus an optional technical detail,
 * over the shared {@link BaseAppException} base; presentation-free (surfaces localize from the
 * kind).
 *
 * <p>Unchecked: it propagates to the CLI boundary, where the shared {@code ExceptionMapper} maps
 * the kind to an exit code and log level. Prefer the {@link #of} / {@link #withDetail} factories
 * over the constructor.
 */
public final class DespeckleException extends BaseAppException {

    private static final long serialVersionUID = 1L;

    private DespeckleException(
            ErrorCategory kind, @Nullable String technicalDetail, @Nullable Throwable cause) {
        super(kind, technicalDetail, cause);
    }

    /** {@return an exception of {@code kind} with no detail or cause} */
    public static DespeckleException of(ErrorCategory kind) {
        return new DespeckleException(kind, null, null);
    }

    /** {@return an exception of {@code kind} wrapping {@code cause}} */
    public static DespeckleException of(ErrorCategory kind, Throwable cause) {
        return new DespeckleException(kind, null, cause);
    }

    /**
     * {@return an exception of {@code kind} with a diagnostic {@code technicalDetail}, optionally
     * wrapping {@code cause}}
     */
    public static DespeckleException withDetail(
            ErrorCategory kind, String technicalDetail, @Nullable Throwable cause) {
        return new DespeckleException(kind, technicalDetail, cause);
    }
}
