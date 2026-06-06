package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class CliExceptionHandlerTest {

    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    private final PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);

    private String err() {
        return errBuf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void mapsIllegalArgumentToUsageDataExitAndPrintsMaskedMessage() {
        int code = new CliExceptionHandler(() -> false, err).handle(new IllegalArgumentException());

        assertThat(code).isEqualTo(ExitCodes.USAGE_DATA);
        assertThat(err()).contains("Error[INVALID_PARAMETER]:");
    }

    @Test
    void mapsIoExceptionToInternalExit() {
        int code = new CliExceptionHandler(() -> false, err).handle(new IOException("io"));

        assertThat(code).isEqualTo(ExitCodes.INTERNAL);
        assertThat(err()).contains("Error[INTERNAL]:");
    }

    @Test
    void nonVerboseOomEmitsTheHeapHint() {
        int code = new CliExceptionHandler(() -> false, err).handle(new OutOfMemoryError("heap"));

        assertThat(code).isEqualTo(ExitCodes.OOM);
        assertThat(err()).contains("Error[OUT_OF_MEMORY]:").contains("hint");
    }

    @Test
    void verbosePrintsTechnicalDetailAndStackTraceInsteadOfHint() {
        int code = new CliExceptionHandler(() -> true, err).handle(new OutOfMemoryError("heap"));

        assertThat(code).isEqualTo(ExitCodes.OOM);
        // Verbose path shows the detail + stack trace, NOT the heap hint.
        assertThat(err()).contains("detail:").contains("OutOfMemoryError").doesNotContain("hint");
    }

    @Test
    void extraRuleIsConsultedBeforeTheBaseline() {
        // A bare RuntimeException would map to INTERNAL (70); the extra rule reclassifies it.
        CliExceptionHandler handler =
                new CliExceptionHandler(
                        () -> false,
                        err,
                        t ->
                                t instanceof IllegalStateException
                                        ? CommonErrorKind.INVALID_PARAMETER
                                        : null);

        int code = handler.handle(new IllegalStateException("reclassify me"));

        assertThat(code).isEqualTo(ExitCodes.USAGE_DATA);
        assertThat(err()).contains("Error[INVALID_PARAMETER]:");
    }

    @Test
    void extraRuleReturningNullFallsThroughToBaseline() {
        CliExceptionHandler handler = new CliExceptionHandler(() -> false, err, t -> null);

        int code = handler.handle(new IOException("io"));

        assertThat(code).isEqualTo(ExitCodes.INTERNAL);
        assertThat(err()).contains("Error[INTERNAL]:");
    }
}
