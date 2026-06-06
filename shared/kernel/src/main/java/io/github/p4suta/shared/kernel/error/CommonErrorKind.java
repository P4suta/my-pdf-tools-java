package io.github.p4suta.shared.kernel.error;

/**
 * The generic failure categories, each carrying its sysexits exit code, {@link Severity},
 * client-fault flag, and default Japanese message on the constant. An app adds an app-specific
 * {@code ErrorKind} only where a failure carries a distinct exit code or user message; otherwise it
 * reuses one of these.
 *
 * <ul>
 *   <li>{@code 64} = {@code EX_USAGE}-family data error (bad CLI value / precondition).
 *   <li>{@code 137} = {@code 128 + SIGKILL}, the conventional OOM exit.
 *   <li>{@code 70} = {@code EX_SOFTWARE}, internal/unexpected.
 * </ul>
 */
public enum CommonErrorKind implements ErrorCategory {

    /** Bad CLI value or violated precondition. {@code EX_USAGE}-family data error. */
    INVALID_PARAMETER("入力値が不正です。", true, 64, Severity.WARN),

    /** The JVM ran out of heap. {@code 128 + SIGKILL}. */
    OUT_OF_MEMORY("メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。", false, 137, Severity.ERROR),

    /** An unexpected internal failure. {@code EX_SOFTWARE}. */
    INTERNAL("予期しないエラーが発生しました。", false, 70, Severity.ERROR);

    private final String defaultUserMessage;
    private final boolean clientFault;
    private final int exitCode;
    private final Severity severity;

    CommonErrorKind(
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
