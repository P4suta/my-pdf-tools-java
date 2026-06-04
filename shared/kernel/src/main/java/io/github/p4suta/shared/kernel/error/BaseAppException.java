package io.github.p4suta.shared.kernel.error;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The abstract base each app's domain exception (tate's {@code SpreadException}, register's {@code
 * RegisterException}, despeckle's {@code DespeckleException}) extends: an unchecked exception
 * tagging every failure with an {@link ErrorCategory} kind plus a user-facing message and an
 * optional technical detail.
 *
 * <p>Unchecked: it propagates to the CLI boundary, where the shared mapper turns the kind into an
 * exit code and log level. Subclasses expose the {@code of(kind)} / {@code of(kind, cause)} /
 * {@code withDetail(kind, detail, cause)} factory shape over the protected constructor (a base
 * class cannot return the subclass type from a static factory). Generalized from tate's {@code
 * SpreadException}.
 */
public abstract class BaseAppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCategory kind;
    private final String userMessage;
    private final @Nullable String technicalDetail;

    /**
     * @param kind the failure category (non-null)
     * @param userMessage the message to show the user (non-null)
     * @param technicalDetail an optional diagnostic detail kept out of the user message
     * @param cause the underlying cause, if any
     */
    protected BaseAppException(
            ErrorCategory kind,
            String userMessage,
            @Nullable String technicalDetail,
            @Nullable Throwable cause) {
        super(buildMessage(kind, userMessage, technicalDetail), cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
        this.technicalDetail = technicalDetail;
    }

    /** {@return the failure category} */
    public ErrorCategory kind() {
        return kind;
    }

    /** {@return the user-facing message} */
    public String userMessage() {
        return userMessage;
    }

    /** {@return the optional diagnostic detail, or {@code null} if none} */
    public @Nullable String technicalDetail() {
        return technicalDetail;
    }

    /**
     * Builds the {@code [NAME] userMessage (detail)} throwable message subclasses pass to {@code
     * super}; shared so every app exception renders identically.
     *
     * @param kind the failure category
     * @param userMessage the user-facing message
     * @param technicalDetail an optional diagnostic detail
     * @return the composed throwable message
     */
    protected static String buildMessage(
            ErrorCategory kind, String userMessage, @Nullable String technicalDetail) {
        return technicalDetail == null
                ? "[" + kind.name() + "] " + userMessage
                : "[" + kind.name() + "] " + userMessage + " (" + technicalDetail + ")";
    }
}
