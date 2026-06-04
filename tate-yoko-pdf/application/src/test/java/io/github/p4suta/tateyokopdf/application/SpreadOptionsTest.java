package io.github.p4suta.tateyokopdf.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SpreadOptionsTest {

    @Test
    void recordHoldsAllFields() {
        var opt =
                new SpreadOptions(
                        Path.of("a.pdf"),
                        Path.of("b.pdf"),
                        ReadingDirection.LTR,
                        FirstPageMode.COVER,
                        true);
        assertThat(opt.sourcePath()).isEqualTo(Path.of("a.pdf"));
        assertThat(opt.outputPath()).isEqualTo(Path.of("b.pdf"));
        assertThat(opt.direction()).isEqualTo(ReadingDirection.LTR);
        assertThat(opt.firstPageMode()).isEqualTo(FirstPageMode.COVER);
        assertThat(opt.pdfA()).isTrue();
    }

    // The rejects-null tests deliberately pass null to non-null params to exercise
    // the runtime guard; suppress NullAway here so the static analyzer doesn't fight us.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullSourcePath() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpreadOptions(
                                        null,
                                        Path.of("b.pdf"),
                                        ReadingDirection.RTL,
                                        FirstPageMode.STANDARD,
                                        false));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullOutputPath() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpreadOptions(
                                        Path.of("a.pdf"),
                                        null,
                                        ReadingDirection.RTL,
                                        FirstPageMode.STANDARD,
                                        false));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullDirection() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpreadOptions(
                                        Path.of("a.pdf"),
                                        Path.of("b.pdf"),
                                        null,
                                        FirstPageMode.STANDARD,
                                        false));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullFirstPageMode() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpreadOptions(
                                        Path.of("a.pdf"),
                                        Path.of("b.pdf"),
                                        ReadingDirection.RTL,
                                        null,
                                        false));
    }

    @Test
    void withDefaultsDerivesOutputFromSourceName() {
        var opt = SpreadOptions.withDefaults(Path.of("/tmp/foo.pdf"));
        assertThat(opt.outputPath()).isEqualTo(Path.of("/tmp/foo_spread.pdf"));
        assertThat(opt.direction()).isEqualTo(ReadingDirection.DEFAULT);
        assertThat(opt.firstPageMode()).isEqualTo(FirstPageMode.STANDARD);
        assertThat(opt.pdfA()).isFalse();
    }

    @Test
    void withDefaultsCaseInsensitivePdfExtension() {
        assertThat(SpreadOptions.withDefaults(Path.of("a.PDF")).outputPath())
                .isEqualTo(Path.of("a_spread.pdf"));
        assertThat(SpreadOptions.withDefaults(Path.of("a.Pdf")).outputPath())
                .isEqualTo(Path.of("a_spread.pdf"));
    }

    @Test
    void withDefaultsLeavesNamesWithoutPdfExtensionUnchanged() {
        // The replaceFirst regex requires a trailing .pdf to swap; without it the output equals
        // input.
        assertThat(SpreadOptions.withDefaults(Path.of("foo")).outputPath())
                .isEqualTo(Path.of("foo"));
    }

    @Test
    void withDefaultsResolvesAlongsideSource() {
        var opt = SpreadOptions.withDefaults(Path.of("/some/dir/x.pdf"));
        assertThat(opt.outputPath().getParent()).isEqualTo(Path.of("/some/dir"));
    }

    @Test
    void withDefaultsRejectsPathWithoutFileName() {
        // A root path ("/") has no file-name element, so an output name cannot be derived.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpreadOptions.withDefaults(Path.of("/")));
    }
}
