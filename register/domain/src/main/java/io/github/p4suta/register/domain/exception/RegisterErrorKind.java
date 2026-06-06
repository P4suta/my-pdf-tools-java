package io.github.p4suta.register.domain.exception;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;

/**
 * The register-specific failure categories, each carrying its sysexits exit code, {@link Severity}
 * and client-fault flag on the constant. Generic failures (bad value / OOM / internal) reuse {@link
 * CommonErrorKind} instead; only the kinds with a distinct exit code live here.
 *
 * <p>Presentation-free: user text is resolved per surface from the stable constant name (CLI
 * English catalog / web Japanese catalog).
 *
 * <p>Invariant: {@code clientFault=true} pairs with {@link Severity#WARN}, {@code
 * clientFault=false} with {@link Severity#ERROR}.
 */
public enum RegisterErrorKind implements ErrorCategory {

    /** A required input file or directory does not exist. {@code EX_NOINPUT}. */
    INPUT_NOT_FOUND(true, 66, Severity.WARN),

    /**
     * An image could not be read: unsupported format, corrupt, or unreadable. {@code EX_DATAERR}.
     */
    IMAGE_UNREADABLE(true, 65, Severity.WARN),

    /** The output already exists and {@code --force} was not given. {@code EX_CANTCREAT}. */
    OUTPUT_CONFLICT(true, 73, Severity.WARN),

    /** An external native tool was missing, failed, or timed out. {@code EX_SOFTWARE}. */
    NATIVE_TOOL_FAILED(false, 70, Severity.ERROR);

    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    RegisterErrorKind(boolean clientFault, int exitCode, Severity severity) {
        this.clientFault = clientFault;
        this.exitCode = exitCode;
        this.severity = severity;
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
