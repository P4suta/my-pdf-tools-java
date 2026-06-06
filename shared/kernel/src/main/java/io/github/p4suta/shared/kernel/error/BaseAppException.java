package io.github.p4suta.shared.kernel.error;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The abstract base each app's domain exception extends: an unchecked exception tagging every
 * failure with an {@link ErrorCategory} kind plus an optional technical detail.
 *
 * <p>Presentation-free: it carries no user-facing message. The kind is the stable, language-neutral
 * identity; each surface resolves the text it shows from that kind (CLI English catalog / web
 * Japanese catalog). The throwable {@link #getMessage()} is a developer/log string ({@code [NAME]
 * detail}), never user-facing prose.
 *
 * <p>Unchecked: it propagates to the CLI boundary, where the mapper turns the kind into an exit
 * code and log level. Subclasses expose the {@code of(kind)} / {@code of(kind, cause)} / {@code
 * withDetail(kind, detail, cause)} factory shape over the protected constructor, since a base class
 * cannot return the subclass type from a static factory.
 */
public abstract class BaseAppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCategory kind;
    private final @Nullable String technicalDetail;

    /**
     * @param kind the failure category (non-null)
     * @param technicalDetail an optional diagnostic detail
     * @param cause the underlying cause, if any
     */
    protected BaseAppException(
            ErrorCategory kind, @Nullable String technicalDetail, @Nullable Throwable cause) {
        super(buildMessage(kind, technicalDetail), cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.technicalDetail = technicalDetail;
    }

    /** {@return the failure category} */
    public ErrorCategory kind() {
        return kind;
    }

    /** {@return the optional diagnostic detail, or {@code null} if none} */
    public @Nullable String technicalDetail() {
        return technicalDetail;
    }

    /**
     * Builds the developer/log throwable message {@code [NAME] detail} subclasses pass to {@code
     * super}; the detail is omitted when absent. This is never user-facing — surfaces localize from
     * {@link #kind()}.
     *
     * @param kind the failure category
     * @param technicalDetail an optional diagnostic detail
     * @return the composed throwable message
     */
    protected static String buildMessage(ErrorCategory kind, @Nullable String technicalDetail) {
        return technicalDetail == null
                ? "[" + kind.name() + "]"
                : "[" + kind.name() + "] " + technicalDetail;
    }
}
