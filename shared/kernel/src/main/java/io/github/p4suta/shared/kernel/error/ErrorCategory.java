package io.github.p4suta.shared.kernel.error;

import java.io.Serializable;

/**
 * The contract a failure category implements: a stable name, a default Japanese user-facing
 * message, a client-fault flag, a sysexits-flavored process exit code, and a {@link Severity}.
 * {@link CommonErrorKind} and each app's {@code ErrorKind} are enums that implement this interface,
 * so the exit code and severity live ON the enum constant rather than in a side table in the
 * mapper. The mapper keeps only the {@code Severity -> slf4j Level} translation and the
 * throwable&rarr;kind fallback.
 *
 * <p>Extends {@link Serializable} so that {@link BaseAppException} — which holds an {@code
 * ErrorCategory} as a non-transient field — serializes cleanly. Every implementor is an enum, which
 * is already serializable, so this constraint costs nothing at the call sites.
 */
public interface ErrorCategory extends Serializable {

    /** {@return the default Japanese, user-facing message for this category} */
    String defaultUserMessage();

    /**
     * {@return whether this failure is the caller's fault (bad input/usage) rather than
     * internal/environmental}
     */
    boolean isClientFault();

    /** {@return the sysexits-flavored process exit code for this category} */
    int exitCode();

    /** {@return the log severity for this category} */
    Severity severity();

    /**
     * {@return the stable identifier of this category (the enum constant name); appears in {@code
     * Error[NAME]: ...}}
     */
    String name();
}
