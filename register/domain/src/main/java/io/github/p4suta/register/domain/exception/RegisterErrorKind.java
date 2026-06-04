package io.github.p4suta.register.domain.exception;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;

/**
 * The register-specific failure categories, each carrying its sysexits exit code, {@link Severity},
 * client-fault flag, and default Japanese message ON the constant (per section&nbsp;3.2 of the
 * error-model spec). Generic failures (bad value / OOM / internal) reuse {@link CommonErrorKind}
 * instead; only the four kinds with a genuinely distinct exit code or user message live here.
 *
 * <ul>
 *   <li>{@link #INPUT_NOT_FOUND} ({@code 66}, {@code EX_NOINPUT}) — a required input file or
 *       directory is missing; split out of {@link #IMAGE_UNREADABLE} to mirror tate's
 *       NOT_FOUND/CORRUPTED distinction.
 *   <li>{@link #IMAGE_UNREADABLE} ({@code 65}, {@code EX_DATAERR}) — an image could not be read
 *       (unsupported format, corrupt, or unreadable).
 *   <li>{@link #OUTPUT_CONFLICT} ({@code 73}, {@code EX_CANTCREAT}) — the output already exists and
 *       {@code --force} was not given.
 *   <li>{@link #NATIVE_TOOL_FAILED} ({@code 70}, {@code EX_SOFTWARE}) — an external tool ({@code
 *       pdfimages}/{@code pdfinfo}/{@code jbig2}) was missing, failed, or timed out.
 * </ul>
 *
 * <p>Per the section&nbsp;1.3 invariant, {@code clientFault=true} pairs with {@link Severity#WARN}
 * and {@code clientFault=false} with {@link Severity#ERROR}.
 */
public enum RegisterErrorKind implements ErrorCategory {

    /** A required input file or directory does not exist. {@code EX_NOINPUT}. */
    INPUT_NOT_FOUND("入力ファイルまたはディレクトリが見つかりません。", true, 66, Severity.WARN),

    /**
     * An image could not be read: unsupported format, corrupt, or unreadable. {@code EX_DATAERR}.
     */
    IMAGE_UNREADABLE("画像を読み込めませんでした。対応していない形式か、ファイルが破損している可能性があります。", true, 65, Severity.WARN),

    /** The output already exists and {@code --force} was not given. {@code EX_CANTCREAT}. */
    OUTPUT_CONFLICT("出力先がすでに存在します。--force で上書きできます。", true, 73, Severity.WARN),

    /** An external native tool was missing, failed, or timed out. {@code EX_SOFTWARE}. */
    NATIVE_TOOL_FAILED(
            "外部ツールの実行に失敗しました。pdfimages / pdfinfo / jbig2 がインストールされているか確認してください。",
            false,
            70,
            Severity.ERROR);

    private final String defaultUserMessage;
    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    RegisterErrorKind(
            String defaultUserMessage, boolean clientFault, int exitCode, Severity severity) {
        this.defaultUserMessage = defaultUserMessage;
        this.clientFault = clientFault;
        this.exitCode = exitCode;
        this.severity = severity;
    }

    @Override
    public String defaultUserMessage() {
        return defaultUserMessage;
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
