package io.github.p4suta.shared.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Asserts {@link CommonErrorKind}'s exit code, severity, client-fault flag, and message per
 * constant, plus the invariant (clientFault &hArr; WARN).
 */
final class CommonErrorKindTest {

    @Test
    void invalidParameterMatchesSpec() {
        CommonErrorKind k = CommonErrorKind.INVALID_PARAMETER;
        assertThat(k.exitCode()).isEqualTo(64);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.defaultUserMessage()).isEqualTo("入力値が不正です。");
        assertThat(k.name()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void outOfMemoryMatchesSpec() {
        CommonErrorKind k = CommonErrorKind.OUT_OF_MEMORY;
        assertThat(k.exitCode()).isEqualTo(137);
        assertThat(k.severity()).isEqualTo(Severity.ERROR);
        assertThat(k.isClientFault()).isFalse();
        assertThat(k.defaultUserMessage())
                .isEqualTo("メモリが不足しました。-Xmx を増やすか、ページ数の少ない PDF で試してください。");
        assertThat(k.name()).isEqualTo("OUT_OF_MEMORY");
    }

    @Test
    void internalMatchesSpec() {
        CommonErrorKind k = CommonErrorKind.INTERNAL;
        assertThat(k.exitCode()).isEqualTo(70);
        assertThat(k.severity()).isEqualTo(Severity.ERROR);
        assertThat(k.isClientFault()).isFalse();
        assertThat(k.defaultUserMessage()).isEqualTo("予期しないエラーが発生しました。");
        assertThat(k.name()).isEqualTo("INTERNAL");
    }

    /**
     * Invariant: {@code isClientFault() == true} ⟹ {@code severity() == WARN}, and {@code
     * isClientFault() == false} ⟹ {@code severity() == ERROR}. No constant may use INFO.
     */
    @ParameterizedTest
    @EnumSource(CommonErrorKind.class)
    void severityTracksClientFault(CommonErrorKind k) {
        assertThat(k.severity()).isNotEqualTo(Severity.INFO);
        if (k.isClientFault()) {
            assertThat(k.severity()).isEqualTo(Severity.WARN);
        } else {
            assertThat(k.severity()).isEqualTo(Severity.ERROR);
        }
    }

    @Test
    void everyKindHasANonBlankMessageAndName() {
        for (CommonErrorKind k : CommonErrorKind.values()) {
            assertThat(k.defaultUserMessage()).isNotBlank();
            assertThat(k.name()).isNotBlank();
        }
    }
}
