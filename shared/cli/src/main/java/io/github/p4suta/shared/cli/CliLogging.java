package io.github.p4suta.shared.cli;

/**
 * Turns on DEBUG logging for the application's own loggers when {@code --verbose} is given.
 *
 * <p>slf4j-simple reads each logger's threshold live from the {@code
 * org.slf4j.simpleLogger.log.<name>} system property when that logger is first constructed (walking
 * up the dotted name). The shared {@code defaultLogLevel} is cached at slf4j-simple's first init —
 * already done by the time a CLI parses its arguments — so only the per-logger key takes effect
 * afterwards. Scoping it to {@code io.github.p4suta} raises every app + shared logger to DEBUG
 * while leaving third-party loggers (PDFBox etc.) at their default level.
 */
public final class CliLogging {

    private static final String ROOT_PACKAGE = "io.github.p4suta";

    private CliLogging() {}

    /**
     * Raises the {@code io.github.p4suta.*} loggers to DEBUG; call before the work loggers load.
     */
    public static void enableDebug() {
        System.setProperty("org.slf4j.simpleLogger.log." + ROOT_PACKAGE, "debug");
    }
}
