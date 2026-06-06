package io.github.p4suta.shared.kernel.error;

/**
 * The generic failure categories, each carrying its sysexits exit code, {@link Severity}, and
 * client-fault flag on the constant. An app adds an app-specific {@code ErrorKind} only where a
 * failure carries a distinct exit code; otherwise it reuses one of these.
 *
 * <p>Presentation-free: no user-facing message lives here. User text is resolved per surface from
 * the stable constant name (CLI English catalog / web Japanese catalog).
 *
 * <ul>
 *   <li>{@code 64} = {@code EX_USAGE}-family data error (bad CLI value / precondition).
 *   <li>{@code 73} = {@code EX_CANTCREAT}, an output already exists (pass {@code --force}).
 *   <li>{@code 137} = {@code 128 + SIGKILL}, the conventional OOM exit.
 *   <li>{@code 70} = {@code EX_SOFTWARE}, internal/unexpected.
 * </ul>
 */
public enum CommonErrorKind implements ErrorCategory {

    /** Bad CLI value or violated precondition. {@code EX_USAGE}-family data error. */
    INVALID_PARAMETER(true, 64, Severity.WARN),

    /** The output path already exists and {@code --force} was not given. {@code EX_CANTCREAT}. */
    OUTPUT_CONFLICT(true, 73, Severity.WARN),

    /** The JVM ran out of heap. {@code 128 + SIGKILL}. */
    OUT_OF_MEMORY(false, 137, Severity.ERROR),

    /** An unexpected internal failure. {@code EX_SOFTWARE}. */
    INTERNAL(false, 70, Severity.ERROR);

    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    CommonErrorKind(boolean clientFault, int exitCode, Severity severity) {
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
