package io.github.p4suta.shared.cli;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.observability.ExceptionMapper;
import java.io.PrintStream;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Turns an exception thrown by a processing pipeline into a user-facing {@code Error[KIND]: ...}
 * line plus a sysexits-flavored exit code. Kept framework-agnostic (no Commons CLI types) so it can
 * be called from a plain try/catch in any front end.
 *
 * <p>The kind, exit code, and (verbose) technical detail come from the shared {@link
 * ExceptionMapper}, which reads the exit code and severity off the resolved {@link ErrorCategory}
 * and masks absolute paths via {@code PiiSanitizer}. The English message is the CLI's own
 * presentation, resolved from the kind via {@link CliErrorMessages} — the kernel carries no text.
 * An app may pass an extra {@code throwable -> ErrorCategory} rule (e.g. an {@code
 * UncheckedIOException} unwrap) that the mapper consults before its shared baseline.
 */
public final class CliExceptionHandler {

    private final BooleanSupplier verboseSupplier;
    private final PrintStream err;
    private final Function<Throwable, @Nullable ErrorCategory> extraRule;

    /**
     * Reports to {@code System.err}, using only the shared baseline classification.
     *
     * @param verboseSupplier whether to print the technical detail and stack trace
     */
    public CliExceptionHandler(BooleanSupplier verboseSupplier) {
        this(verboseSupplier, System.err);
    }

    /**
     * Reports to {@code System.err}, consulting {@code extraRule} before the shared baseline.
     *
     * @param verboseSupplier whether to print the technical detail and stack trace
     * @param extraRule an app-supplied {@code throwable -> ErrorCategory} hook; returns {@code
     *     null} to defer to the baseline
     */
    public CliExceptionHandler(
            BooleanSupplier verboseSupplier,
            Function<Throwable, @Nullable ErrorCategory> extraRule) {
        this(verboseSupplier, System.err, extraRule);
    }

    CliExceptionHandler(BooleanSupplier verboseSupplier, PrintStream err) {
        this(verboseSupplier, err, t -> null);
    }

    CliExceptionHandler(
            BooleanSupplier verboseSupplier,
            PrintStream err,
            Function<Throwable, @Nullable ErrorCategory> extraRule) {
        this.verboseSupplier = verboseSupplier;
        this.err = err;
        this.extraRule = extraRule;
    }

    /**
     * Reports {@code ex} to stderr and returns the exit code the process should terminate with.
     *
     * @param ex the throwable to classify and report
     * @return the process exit code from the resolved {@link ErrorCategory}
     */
    public int handle(Throwable ex) {
        ExceptionMapper.Mapping mapping = ExceptionMapper.map(ex, extraRule);
        err.println("Error[" + mapping.kind().name() + "]: " + CliErrorMessages.of(mapping.kind()));
        if (verboseSupplier.getAsBoolean()) {
            if (mapping.technicalDetail() != null) {
                err.println("  detail: " + mapping.technicalDetail());
            }
            ex.printStackTrace(err);
        } else if (mapping.kind() == CommonErrorKind.OUT_OF_MEMORY) {
            err.println("  hint: increase the JVM max heap (e.g. -Xmx1g)");
        }
        return mapping.exitCode();
    }
}
