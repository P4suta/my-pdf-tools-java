package io.github.p4suta.register.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.register.domain.exception.RegisterErrorKind;
import io.github.p4suta.shared.cli.CliErrorMessages;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every register error kind the CLI can surface has an English entry in the CLI catalog. */
final class RegisterMessagesCoverageTest {

    @ParameterizedTest
    @EnumSource(RegisterErrorKind.class)
    void everyKindHasAnEnglishCliMessage(RegisterErrorKind kind) {
        assertThat(CliErrorMessages.has(kind.name()))
                .as("missing English CLI message for %s", kind.name())
                .isTrue();
    }
}
