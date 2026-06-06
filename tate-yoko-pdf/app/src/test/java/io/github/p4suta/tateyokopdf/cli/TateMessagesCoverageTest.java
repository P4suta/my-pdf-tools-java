package io.github.p4suta.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.cli.CliErrorMessages;
import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every tate-yoko-pdf error kind the CLI can surface has an English entry in the CLI catalog. */
final class TateMessagesCoverageTest {

    @ParameterizedTest
    @EnumSource(ErrorKind.class)
    void everyKindHasAnEnglishCliMessage(ErrorKind kind) {
        assertThat(CliErrorMessages.has(kind.name()))
                .as("missing English CLI message for %s", kind.name())
                .isTrue();
    }
}
