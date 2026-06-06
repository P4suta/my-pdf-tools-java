package io.github.p4suta.despeckle.domain.exception;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;

/**
 * The despeckle-specific failure categories that carry a genuinely distinct exit code or user
 * message, each implementing {@link ErrorCategory} so its sysexits exit code and {@link Severity}
 * live ON the constant (per section&nbsp;3.3 of the error-model spec). Generic failures
 * (invalid-param / OOM / internal) reuse {@link CommonErrorKind} instead of being duplicated here.
 *
 * <ul>
 *   <li>{@link #INPUT_NOT_FOUND} ({@code 66}, {@code EX_NOINPUT}) — a missing input PDF or image
 *       directory.
 *   <li>{@link #IMAGE_UNREADABLE} ({@code 65}, {@code EX_DATAERR}) — Leptonica could not read an
 *       image (unsupported format or corrupt file).
 *   <li>{@link #OUTPUT_CONFLICT} ({@code 73}, {@code EX_CANTCREAT}) — the output already exists and
 *       {@code --force} was not given.
 *   <li>{@link #NATIVE_TOOL_FAILED} ({@code 70}, {@code EX_SOFTWARE}) — an external tool ({@code
 *       pdfimages} / {@code pdfinfo} / {@code jbig2} / {@code qpdf}, or an ImageIO PNG writer) was
 *       missing, failed, or timed out.
 * </ul>
 *
 * <p>Per the section&nbsp;1.3 invariant, every {@code clientFault=true} kind logs at {@link
 * Severity#WARN} and every {@code clientFault=false} kind at {@link Severity#ERROR}.
 */
public enum DespeckleErrorKind implements ErrorCategory {

    /** A required input file or directory does not exist. {@code EX_NOINPUT}. */
    INPUT_NOT_FOUND(true, 66, Severity.WARN),

    /** An image could not be read (unsupported format or corrupt file). {@code EX_DATAERR}. */
    IMAGE_UNREADABLE(true, 65, Severity.WARN),

    /** The output already exists and {@code --force} was not given. {@code EX_CANTCREAT}. */
    OUTPUT_CONFLICT(true, 73, Severity.WARN),

    /** An external native tool was missing, failed, or timed out. {@code EX_SOFTWARE}. */
    NATIVE_TOOL_FAILED(false, 70, Severity.ERROR);

    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    DespeckleErrorKind(boolean clientFault, int exitCode, Severity severity) {
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
