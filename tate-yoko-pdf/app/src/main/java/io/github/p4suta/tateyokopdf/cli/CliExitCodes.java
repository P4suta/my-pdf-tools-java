package io.github.p4suta.tateyokopdf.cli;

/**
 * sysexits.h-flavored exit codes for the CLI. Picked for compatibility with shell scripts that
 * already know the BSD conventions (65=EX_DATAERR, 66=EX_NOINPUT, 73=EX_CANTCREAT, 74=EX_IOERR,
 * 77=EX_NOPERM, 78=EX_CONFIG, 70=EX_SOFTWARE). 137 = 128 + SIGKILL is conventional for OOM.
 */
public final class CliExitCodes {

    public static final int OK = 0;
    public static final int GENERIC_ERROR = 1;
    public static final int USAGE = 2;
    public static final int INPUT_DATA = 65;
    public static final int INPUT_NOTFOUND = 66;
    public static final int INTERNAL = 70;
    public static final int OUTPUT_WRITE = 73;
    public static final int IO_ERROR = 74;
    public static final int PASSWORD = 77;
    public static final int CONFIG = 78;
    public static final int OOM = 137;

    private CliExitCodes() {}
}
