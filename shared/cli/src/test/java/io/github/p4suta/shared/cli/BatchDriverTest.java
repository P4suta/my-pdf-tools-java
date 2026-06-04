package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.observability.ExitCodes;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

final class BatchDriverTest {

    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    private BatchDriver<String> driver() {
        return new BatchDriver<>(
                LoggerFactory.getLogger(BatchDriverTest.class),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String err() {
        return errBuf.toString(StandardCharsets.UTF_8);
    }

    private static BatchDriver.ItemLabeler<String> indexed() {
        return (item, index, total) -> "[" + index + "/" + total + "] " + item;
    }

    @org.junit.jupiter.api.Test
    void emptyBatchSucceedsWithoutOutput() {
        int code = driver().run(List.of(), indexed(), item -> {});

        assertThat(code).isEqualTo(ExitCodes.OK);
        assertThat(err()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void allItemsSucceedProcessesEveryItemAndReturnsOk() {
        List<String> processed = new ArrayList<>();

        int code = driver().run(List.of("a", "b", "c"), indexed(), processed::add);

        assertThat(code).isEqualTo(ExitCodes.OK);
        assertThat(processed).containsExactly("a", "b", "c");
        assertThat(err()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void continuesPastFailuresAndReturnsFirstFailureExitCode() {
        List<String> processed = new ArrayList<>();

        int code =
                driver().run(
                                List.of("ok1", "badArg", "ok2", "io"),
                                indexed(),
                                item -> {
                                    processed.add(item);
                                    if ("badArg".equals(item)) {
                                        throw new IllegalArgumentException("bad value");
                                    }
                                    if ("io".equals(item)) {
                                        throw new java.io.IOException("disk full");
                                    }
                                });

        // Every item was attempted (continue-on-error), not just up to the first failure.
        assertThat(processed).containsExactly("ok1", "badArg", "ok2", "io");
        // First failure was the IllegalArgumentException -> INVALID_PARAMETER -> exit 64.
        assertThat(code).isEqualTo(ExitCodes.USAGE_DATA);
        assertThat(err())
                .contains("Error[INVALID_PARAMETER] [2/4] badArg")
                .contains("Error[INTERNAL] [4/4] io")
                .contains("2 of 4 items failed.");
    }

    @org.junit.jupiter.api.Test
    void singleFailureReturnsThatItemsExitCode() {
        int code =
                driver().run(
                                List.of("io"),
                                indexed(),
                                item -> {
                                    throw new java.io.IOException("disk full");
                                });

        // IOException -> INTERNAL -> exit 70.
        assertThat(code).isEqualTo(ExitCodes.INTERNAL);
        assertThat(err()).contains("Error[INTERNAL]").contains("1 of 1 items failed.");
    }
}
