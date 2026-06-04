package io.github.p4suta.shared.observability;

/**
 * The sysexits.h-flavored process exit-code registry shared across the p4suta CLIs. Picked for
 * compatibility with shell scripts that already know the BSD conventions. {@link
 * io.github.p4suta.shared.kernel.error.ErrorCategory#exitCode()} returns one of these; the
 * constants exist so call sites and tests reference a name rather than a bare integer.
 *
 * <p>{@code 137 = 128 + SIGKILL} is the conventional OOM exit. {@code USAGE} ({@code 2}) is the
 * Commons CLI parse-error code the CLIs return outside the {@link
 * io.github.p4suta.shared.kernel.error.ErrorCategory} path. {@code IO_ERROR} ({@code 74}) and
 * {@code CONFIG} ({@code 78}) are defined for completeness but no current {@code ErrorCategory}
 * maps to them.
 */
public final class ExitCodes {

    /** Successful run. {@code EX_OK}. */
    public static final int OK = 0;

    /** Unspecified failure. Retired in favor of the specific sysexits codes; kept for reference. */
    public static final int GENERIC = 1;

    /** Commons CLI usage / argument-parsing error (NOT routed through {@code ErrorCategory}). */
    public static final int USAGE = 2;

    /** Bad CLI value / violated precondition. {@code EX_USAGE}. */
    public static final int USAGE_DATA = 64;

    /** Input data is malformed / unreadable. {@code EX_DATAERR}. */
    public static final int INPUT_DATA = 65;

    /** A required input file or directory does not exist. {@code EX_NOINPUT}. */
    public static final int NO_INPUT = 66;

    /** Internal / unexpected software failure. {@code EX_SOFTWARE}. */
    public static final int INTERNAL = 70;

    /** An output file could not be created. {@code EX_CANTCREAT}. */
    public static final int WRITE = 73;

    /** An I/O error occurred. {@code EX_IOERR}. Defined for completeness; currently unused. */
    public static final int IO_ERROR = 74;

    /** Permission denied / password-protected. {@code EX_NOPERM}. */
    public static final int NOPERM = 77;

    /**
     * Permission denied / password-protected — alias of {@link #NOPERM} ({@code 77}) for call sites
     * that read the failure as a password problem (e.g. an encrypted PDF).
     */
    public static final int PASSWORD = 77;

    /** Configuration error. {@code EX_CONFIG}. Defined for completeness; currently unused. */
    public static final int CONFIG = 78;

    /** Out of memory. {@code 128 + SIGKILL}, the conventional OOM exit status. */
    public static final int OOM = 137;

    private ExitCodes() {}
}
