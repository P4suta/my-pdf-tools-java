package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.CommonErrorKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the CLI English catalog: every shared {@link CommonErrorKind} has an English entry, the
 * messages are ASCII, and an unknown kind degrades to its bare name rather than throwing. App-kind
 * coverage is asserted in each tool's own {@code *MessagesCoverageTest}.
 */
final class CliErrorMessagesTest {

    @ParameterizedTest
    @EnumSource(CommonErrorKind.class)
    void everyCommonKindHasAnAsciiEnglishEntry(CommonErrorKind kind) {
        assertThat(CliErrorMessages.has(kind.name())).isTrue();
        assertThat(CliErrorMessages.of(kind)).isNotBlank().matches("\\p{ASCII}+");
    }

    @Test
    void unknownKindFallsBackToItsName() {
        io.github.p4suta.shared.kernel.error.ErrorCategory unknown =
                new io.github.p4suta.shared.kernel.error.ErrorCategory() {
                    @Override
                    public boolean isClientFault() {
                        return false;
                    }

                    @Override
                    public int exitCode() {
                        return 70;
                    }

                    @Override
                    public io.github.p4suta.shared.kernel.error.Severity severity() {
                        return io.github.p4suta.shared.kernel.error.Severity.ERROR;
                    }

                    @Override
                    public String name() {
                        return "TOTALLY_UNKNOWN_KIND";
                    }
                };
        assertThat(CliErrorMessages.has("TOTALLY_UNKNOWN_KIND")).isFalse();
        assertThat(CliErrorMessages.of(unknown)).isEqualTo("TOTALLY_UNKNOWN_KIND");
    }
}
