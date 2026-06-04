package io.github.p4suta.despeckle.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.kernel.error.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins {@link DespeckleErrorKind} to section&nbsp;3.3 of the error-model spec — the spec table is
 * the oracle, not the enum's own code. Exit code, severity, client-fault flag, and message are
 * asserted per constant, plus the section&nbsp;1.3 invariant (clientFault &hArr; WARN).
 */
final class DespeckleErrorKindTest {

    @Test
    void inputNotFoundMatchesSpec() {
        DespeckleErrorKind k = DespeckleErrorKind.INPUT_NOT_FOUND;
        assertThat(k.exitCode()).isEqualTo(66);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.defaultUserMessage()).isEqualTo("入力ファイルまたはディレクトリが見つかりません。");
        assertThat(k.name()).isEqualTo("INPUT_NOT_FOUND");
    }

    @Test
    void imageUnreadableMatchesSpec() {
        DespeckleErrorKind k = DespeckleErrorKind.IMAGE_UNREADABLE;
        assertThat(k.exitCode()).isEqualTo(65);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.defaultUserMessage())
                .isEqualTo("画像を読み込めませんでした。対応していない形式か、ファイルが破損している可能性があります。");
        assertThat(k.name()).isEqualTo("IMAGE_UNREADABLE");
    }

    @Test
    void outputConflictMatchesSpec() {
        DespeckleErrorKind k = DespeckleErrorKind.OUTPUT_CONFLICT;
        assertThat(k.exitCode()).isEqualTo(73);
        assertThat(k.severity()).isEqualTo(Severity.WARN);
        assertThat(k.isClientFault()).isTrue();
        assertThat(k.defaultUserMessage()).isEqualTo("出力先がすでに存在します。--force で上書きできます。");
        assertThat(k.name()).isEqualTo("OUTPUT_CONFLICT");
    }

    @Test
    void nativeToolFailedMatchesSpec() {
        DespeckleErrorKind k = DespeckleErrorKind.NATIVE_TOOL_FAILED;
        assertThat(k.exitCode()).isEqualTo(70);
        assertThat(k.severity()).isEqualTo(Severity.ERROR);
        assertThat(k.isClientFault()).isFalse();
        assertThat(k.defaultUserMessage())
                .isEqualTo(
                        "外部ツールの実行に失敗しました。pdfimages / pdfinfo / jbig2 / qpdf"
                                + " がインストールされているか確認してください。");
        assertThat(k.name()).isEqualTo("NATIVE_TOOL_FAILED");
    }

    /**
     * Section&nbsp;1.3 invariant: {@code isClientFault() == true} ⟹ {@code severity() == WARN}, and
     * {@code isClientFault() == false} ⟹ {@code severity() == ERROR}. No constant may use INFO.
     */
    @ParameterizedTest
    @EnumSource(DespeckleErrorKind.class)
    void severityTracksClientFault(DespeckleErrorKind k) {
        assertThat(k.severity()).isNotEqualTo(Severity.INFO);
        if (k.isClientFault()) {
            assertThat(k.severity()).isEqualTo(Severity.WARN);
        } else {
            assertThat(k.severity()).isEqualTo(Severity.ERROR);
        }
    }

    @Test
    void everyKindHasANonBlankMessageAndName() {
        for (DespeckleErrorKind k : DespeckleErrorKind.values()) {
            assertThat(k.defaultUserMessage()).isNotBlank();
            assertThat(k.name()).isNotBlank();
        }
    }
}
