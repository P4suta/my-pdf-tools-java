package io.github.p4suta.tateyokopdf.cli;

import io.github.p4suta.tateyokopdf.application.ProgressListener;
import java.io.PrintStream;
import org.jspecify.annotations.Nullable;

/**
 * Writes human-readable progress to stderr. stderr (not stdout) is used on purpose so that {@code
 * -o -} (write the PDF to stdout) stays a clean binary stream — diagnostics never mix with data.
 *
 * <p>An optional {@code label} is prefixed to each line so batch runs can show which file is being
 * processed (e.g. {@code [2/5] novel.pdf}). With no label the output is identical to the original
 * single-file format.
 */
public class ConsoleProgressListener implements ProgressListener {

    private final PrintStream out;
    private final String prefix;

    public ConsoleProgressListener() {
        this(System.err, null);
    }

    public ConsoleProgressListener(@Nullable String label) {
        this(System.err, label);
    }

    ConsoleProgressListener(PrintStream out, @Nullable String label) {
        this.out = out;
        this.prefix = (label == null || label.isBlank()) ? "" : label + " ";
    }

    @Override
    public void onStart(int totalSpreads) {
        out.printf("%sProcessing %d spreads...%n", prefix, totalSpreads);
    }

    @Override
    public void onSpreadComplete(int current, int total) {
        out.printf("\r%s[%d/%d] spreads completed", prefix, current, total);
    }

    @Override
    public void onComplete(long elapsedMillis) {
        out.printf("%nDone in %.1f seconds.%n", elapsedMillis / 1000.0);
    }
}
