package io.github.p4suta.register.domain.exception;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;

/**
 * The register-specific failure categories, each carrying its sysexits exit code, {@link Severity},
 * client-fault flag, and default Japanese message on the constant. Generic failures (bad value /
 * OOM / internal) reuse {@link CommonErrorKind} instead; only the four kinds with a distinct exit
 * code or user message live here.
 *
 * <p>Invariant: {@code clientFault=true} pairs with {@link Severity#WARN}, {@code
 * clientFault=false} with {@link Severity#ERROR}.
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
