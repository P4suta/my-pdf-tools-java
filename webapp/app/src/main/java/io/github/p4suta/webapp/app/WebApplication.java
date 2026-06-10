package io.github.p4suta.webapp.app;

import java.io.Console;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The pdfbook web front end entry point. */
@SpringBootApplication
@EnableScheduling
public class WebApplication {

    /**
     * Starts the web server.
     *
     * <p>Wraps startup so a bind/boot failure does not just flash and vanish: the double-clicked
     * self-contained app-image opens a {@code --win-console} window that closes the instant the
     * process exits, so an uncaught {@code PortInUseException} (or any startup failure) would leave
     * the user with no readable error. Spring's FailureAnalyzers already print a formatted report
     * before {@link SpringApplication#run} rethrows, so here we add only a short pointer line and —
     * when attached to a real terminal — hold the window open until the user presses Enter. Piped,
     * redirected, CI, and headless runs have no real terminal, so they exit immediately (no hang).
     *
     * @param args the command-line arguments
     */
    // SystemConsoleNull: Error Prone notes System.console() is non-null for redirected streams
    // since JDK 22 — true WITH the JLine provider. But our jlink image omits jdk.internal.le, so it
    // falls back to java.base semantics where System.console() IS null without a real tty; the null
    // check therefore guards a real NPE before isTerminal(), not dead code.
    @SuppressWarnings("SystemConsoleNull")
    public static void main(String[] args) {
        try {
            SpringApplication.run(WebApplication.class, args);
        } catch (Exception e) {
            // The full diagnostic was already printed above by Spring's FailureAnalyzers; don't
            // re-dump it. Just point at it and keep the window readable.
            System.err.print(startupFailureMessage());
            // Console is non-null for redirected streams under the JLine provider, so isTerminal()
            // is what distinguishes a real, interactive terminal — the only place a wait makes
            // sense. The combined guard is correct whether or not the jlink image bundles JLine.
            Console console = System.console();
            if (console != null && console.isTerminal()) {
                console.readLine();
            }
            System.exit(1);
        }
    }

    /**
     * The console message shown when startup fails, pointing at the diagnostic above and (on a real
     * terminal) explaining the Enter-to-close wait. Pure, so it is unit-tested. English, matching
     * the ready banner and the stack trace it sits beneath.
     *
     * @return the multi-line failure message
     */
    static String startupFailureMessage() {
        return System.lineSeparator()
                + "  pdfbook-web failed to start — see the error above."
                + System.lineSeparator()
                + System.lineSeparator()
                + "  Press Enter to close this window…"
                + System.lineSeparator();
    }
}
