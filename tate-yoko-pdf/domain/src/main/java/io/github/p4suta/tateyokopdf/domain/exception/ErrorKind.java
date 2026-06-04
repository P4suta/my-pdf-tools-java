package io.github.p4suta.tateyokopdf.domain.exception;

import io.github.p4suta.shared.kernel.error.ErrorCategory;
import io.github.p4suta.shared.kernel.error.Severity;

/**
 * The taxonomy of failures the conversion can surface. Each kind carries a default Japanese
 * user-facing message, a {@code clientFault} flag (true = caused by the input or usage, false =
 * internal or environmental), a sysexits-flavored process exit code, and a {@link Severity} — the
 * exit code and severity that used to live in the observability mapper's {@code EnumMap} now sit ON
 * the constant, so this enum implements {@link ErrorCategory} directly. The shared {@code
 * ExceptionMapper} reads {@link #exitCode()}/{@link #severity()} off the category. README's
 * troubleshooting table is keyed by these names.
 *
 * <p>Per the section&nbsp;1.3 invariant of the error-model spec, {@link Severity#WARN} pairs with a
 * client fault and {@link Severity#ERROR} with an internal/environmental one.
 */
public enum ErrorKind implements ErrorCategory {
    PDF_CORRUPTED("PDFを読み込めませんでした。ファイルが破損している可能性があります。", true, 65, Severity.WARN),
    PDF_PASSWORD_PROTECTED("PDFがパスワードで保護されているため処理できません。", true, 77, Severity.WARN),
    PDF_NOT_FOUND("指定された PDF ファイルが見つかりません。", true, 66, Severity.WARN),
    PDF_INVALID_PAGE("PDFのページ指定が不正です。", true, 65, Severity.WARN),
    PDF_WRITE_FAILED("出力 PDF の書き出しに失敗しました。", false, 73, Severity.ERROR),
    INVALID_PARAMETER("入力値が不正です。", true, 64, Severity.WARN),
    OUT_OF_MEMORY("メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。", false, 137, Severity.ERROR),
    INTERNAL("予期しないエラーが発生しました。", false, 70, Severity.ERROR);

    private final String defaultUserMessage;
    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    ErrorKind(String defaultUserMessage, boolean clientFault, int exitCode, Severity severity) {
        this.defaultUserMessage = defaultUserMessage;
        this.clientFault = clientFault;
        this.exitCode = exitCode;
        this.severity = severity;
    }

    /** {@return the default Japanese message shown to the user for this kind} */
    @Override
    public String defaultUserMessage() {
        return defaultUserMessage;
    }

    /**
     * {@return whether this failure is the caller's fault (bad input or usage) rather than
     * internal}
     */
    @Override
    public boolean isClientFault() {
        return clientFault;
    }

    /** {@return the sysexits-flavored process exit code for this kind} */
    @Override
    public int exitCode() {
        return exitCode;
    }

    /** {@return the log severity for this kind} */
    @Override
    public Severity severity() {
        return severity;
    }
}
