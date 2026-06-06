package io.github.p4suta.despeckle.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.despeckle.domain.exception.DespeckleErrorKind;
import io.github.p4suta.shared.cli.CliErrorMessages;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every despeckle error kind the CLI can surface has an English entry in the CLI catalog. */
final class DespeckleMessagesCoverageTest {

    @ParameterizedTest
    @EnumSource(DespeckleErrorKind.class)
    void everyKindHasAnEnglishCliMessage(DespeckleErrorKind kind) {
        assertThat(CliErrorMessages.has(kind.name()))
                .as("missing English CLI message for %s", kind.name())
                .isTrue();
    }
}
