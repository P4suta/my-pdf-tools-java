package io.github.p4suta.shared.observability;

import io.github.p4suta.shared.kernel.error.BaseAppException;
import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;
import java.io.IOException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

/**
 * Maps any throwable to a stable {@link ErrorCategory}, a process exit code, an slf4j {@link
 * Level}, and an optional PII-safe (absolute-path-masked) technical detail. Facts only — no
 * user-facing message; each surface localizes from the kind. Data-driven: the exit code and
 * severity are read OFF the {@code ErrorCategory} (no side table). This layer owns only the {@code
 * Severity -> Level} translation and the throwable&rarr;kind fallback.
 *
 * <p>The throwable&rarr;kind fallback, first match wins:
 *
 * <ol>
 *   <li>a {@link BaseAppException} carries its own kind;
 *   <li>{@link IllegalArgumentException} &rarr; {@link CommonErrorKind#INVALID_PARAMETER} (64);
 *   <li>{@link OutOfMemoryError} &rarr; {@link CommonErrorKind#OUT_OF_MEMORY} (137);
 *   <li>{@link IOException} &rarr; {@link CommonErrorKind#INTERNAL} (70);
 *   <li>anything else &rarr; {@link CommonErrorKind#INTERNAL} (70).
 * </ol>
 *
 * <p>An app may supply an extra {@code throwable -> ErrorCategory} rule; it is consulted before the
 * shared baseline but after the {@link BaseAppException} short-circuit (which always wins, as the
 * exception already carries its kind). Returning {@code null} from the rule falls through to the
 * baseline.
 */
public final class ExceptionMapper {

    /**
     * The outcome of mapping a throwable — facts only, no user-facing message. Each surface
     * resolves the text it shows from {@link #kind()} (the CLI from its English catalog, the web UI
     * from its Japanese one).
     *
     * @param kind the resolved error category
     * @param exitCode the process exit code ({@code kind.exitCode()})
     * @param level the slf4j log level ({@code kind.severity()} translated)
     * @param technicalDetail an optional diagnostic detail with absolute paths masked, or {@code
     *     null}
     */
    public record Mapping(
            ErrorCategory kind, int exitCode, Level level, @Nullable String technicalDetail) {}

    private ExceptionMapper() {}

    /**
     * Maps {@code t} using only the shared baseline fallback.
     *
     * @param t the throwable to classify
     * @return the resulting mapping
     */
    public static Mapping map(Throwable t) {
        if (t instanceof BaseAppException app) {
            return mappingFor(app.kind(), app.technicalDetail());
        }
        return mappingFor(fallbackKind(t), detailOf(t));
    }

    /**
     * Maps {@code t}, consulting {@code extraRule} before the shared baseline fallback (a {@link
     * BaseAppException} still wins outright, as it already carries its kind). Returning {@code
     * null} from {@code extraRule} defers to the baseline.
     *
     * @param t the throwable to classify
     * @param extraRule an app-supplied {@code throwable -> ErrorCategory} hook; returns {@code
     *     null} to defer to the baseline
     * @return the resulting mapping
     */
    public static Mapping map(Throwable t, Function<Throwable, @Nullable ErrorCategory> extraRule) {
        if (t instanceof BaseAppException app) {
            return mappingFor(app.kind(), app.technicalDetail());
        }
        ErrorCategory extra = extraRule.apply(t);
        ErrorCategory kind = extra != null ? extra : fallbackKind(t);
        return mappingFor(kind, detailOf(t));
    }

    /** The shared baseline throwable&rarr;kind fallback; order matters, first match wins. */
    private static ErrorCategory fallbackKind(Throwable t) {
        if (t instanceof IllegalArgumentException) {
            return CommonErrorKind.INVALID_PARAMETER;
        }
        if (t instanceof OutOfMemoryError) {
            return CommonErrorKind.OUT_OF_MEMORY;
        }
        if (t instanceof IOException) {
            return CommonErrorKind.INTERNAL;
        }
        return CommonErrorKind.INTERNAL;
    }

    /**
     * The diagnostic detail captured for a non-domain throwable: its message, or its simple class
     * name when the message is {@code null}, so the detail line is never empty.
     */
    private static String detailOf(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private static Mapping mappingFor(ErrorCategory kind, @Nullable String technicalDetail) {
        String maskedDetail =
                technicalDetail == null ? null : PiiSanitizer.maskAbsolutePaths(technicalDetail);
        return new Mapping(kind, kind.exitCode(), toLevel(kind.severity()), maskedDetail);
    }

    /** The single {@link Severity} &rarr; slf4j {@link Level} translation. */
    private static Level toLevel(Severity severity) {
        return switch (severity) {
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }
}
