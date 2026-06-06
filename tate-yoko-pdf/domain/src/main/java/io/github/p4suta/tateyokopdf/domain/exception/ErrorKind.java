package io.github.p4suta.tateyokopdf.domain.exception;

import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;

/**
 * The taxonomy of failures the conversion can surface. Each kind carries a {@code clientFault} flag
 * (true = caused by the input or usage, false = internal or environmental), a sysexits-flavored
 * process exit code, and a {@link Severity}. The exit code and severity sit on the constant via
 * {@link ErrorCategory}; the shared {@code ExceptionMapper} reads {@link #exitCode()}/{@link
 * #severity()} off the category. Presentation-free: user text is resolved per surface from the
 * stable constant name. README's troubleshooting table is keyed by these names.
 *
 * <p>Invariant: {@link Severity#WARN} pairs with a client fault and {@link Severity#ERROR} with an
 * internal/environmental one.
 */
public enum ErrorKind implements ErrorCategory {
    PDF_CORRUPTED(true, 65, Severity.WARN),
    PDF_PASSWORD_PROTECTED(true, 77, Severity.WARN),
    PDF_NOT_FOUND(true, 66, Severity.WARN),
    PDF_INVALID_PAGE(true, 65, Severity.WARN),
    PDF_WRITE_FAILED(false, 73, Severity.ERROR),
    INVALID_PARAMETER(true, 64, Severity.WARN),
    OUT_OF_MEMORY(false, 137, Severity.ERROR),
    INTERNAL(false, 70, Severity.ERROR);

    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    ErrorKind(boolean clientFault, int exitCode, Severity severity) {
        this.clientFault = clientFault;
        this.exitCode = exitCode;
        this.severity = severity;
    }

    /**
     * {@return whether this failure is the caller's fault (bad input or usage) rather than
     * internal}
     */
    @Override
    public boolean isClientFault() {
        return clientFault;
    }

    /** {@return the sysexits-flavored process exit code for this kind} */
    @Override
    public int exitCode() {
        return exitCode;
    }

    /** {@return the log severity for this kind} */
    @Override
    public Severity severity() {
        return severity;
    }
}
