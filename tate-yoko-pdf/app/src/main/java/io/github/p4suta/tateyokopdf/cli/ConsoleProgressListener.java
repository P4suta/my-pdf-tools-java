package io.github.p4suta.tateyokopdf.cli;

import io.github.p4suta.shared.cli.CliConsole;
import io.github.p4suta.tateyokopdf.application.ProgressListener;
import java.io.PrintStream;
import org.jspecify.annotations.Nullable;

/**
 * Writes human-readable progress to stderr. stderr (not stdout) is used on purpose so that {@code
 * -o -} (write the PDF to stdout) stays a clean binary stream — diagnostics never mix with data.
 *
 * <p>An optional {@code label} is prefixed to each line so batch runs can show which file is being
 * processed (e.g. {@code [2/5] novel.pdf}). With no label the lines carry no prefix.
 *
 * <p>On a terminal the per-spread line is rewritten in place with a carriage return; when stderr is
 * redirected ({@link CliConsole#isInteractive()} is false) each update is a separate newline-
 * terminated line, so a redirected log holds clean text rather than carriage-return garbage.
 */
public class ConsoleProgressListener implements ProgressListener {

    private final PrintStream out;
    private final String prefix;
    private final boolean interactive;

    public ConsoleProgressListener() {
        this(System.err, null, CliConsole.isInteractive());
    }

    public ConsoleProgressListener(@Nullable String label) {
        this(System.err, label, CliConsole.isInteractive());
    }

    ConsoleProgressListener(PrintStream out, @Nullable String label, boolean interactive) {
        this.out = out;
        this.prefix = (label == null || label.isBlank()) ? "" : label + " ";
        this.interactive = interactive;
    }

    @Override
    public void onStart(int totalSpreads) {
        out.printf("%sProcessing %d spreads...%n", prefix, totalSpreads);
    }

    @Override
    public void onSpreadComplete(int current, int total) {
        if (interactive) {
            out.printf("\r%s[%d/%d] spreads completed", prefix, current, total);
        } else {
            out.printf("%s[%d/%d] spreads completed%n", prefix, current, total);
        }
    }

    @Override
    public void onComplete(long elapsedMillis) {
        // After an in-place (\r) line, lead with a newline to terminate it; the redirected path
        // already ended its last line, so only the interactive path needs the leading break.
        if (interactive) {
            out.printf("%nDone in %.1f seconds.%n", elapsedMillis / 1000.0);
        } else {
            out.printf("Done in %.1f seconds.%n", elapsedMillis / 1000.0);
        }
    }
}
