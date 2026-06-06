package io.github.p4suta.pipeline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.cli.Ansi;
import io.github.p4suta.shared.cli.Prompt;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the {@code pdfbook -i} wizard through a scripted {@link Prompt}: the prompt-and-filesystem
 * flow that builds a {@link PipelineCommand.Plan}, with no pipeline run or console coupling.
 */
final class PipelineWizardTest {

    private static Prompt scripted(String input) {
        return new Prompt(
                new BufferedReader(new StringReader(input)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new Ansi(false));
    }

    @Test
    void defaultsThroughBuildAnExpectedPlan(@TempDir Path dir) throws Exception {
        Path in = Files.writeString(dir.resolve("scan.pdf"), "x");
        // input, direction(default RTL), first-page(default right), despeckle y, register y,
        // deskew y, scale y, pdf-a default(n), output(default), start(default y).
        String script = in + "\n\n\n\n\n\n\n\n\n\n";

        PipelineCommand.Plan plan = PipelineCommand.plan(scripted(script));

        assertThat(plan).isNotNull();
        assertThat(plan.input()).isEqualTo(in);
        assertThat(plan.output()).isEqualTo(dir.resolve("scan_book.pdf").toAbsolutePath());
        PipelineCommand.Config c = plan.config();
        assertThat(c.direction()).isEqualTo(ReadingDirection.RTL);
        assertThat(c.firstPage()).isEqualTo(FirstPageMode.STANDARD);
        assertThat(c.despeckle()).isTrue();
        assertThat(c.register()).isTrue();
        assertThat(c.pdfA()).isFalse();
        assertThat(c.force()).isFalse();
    }

    @Test
    void choicesAndTogglesAreHonored(@TempDir Path dir) throws Exception {
        Path in = Files.writeString(dir.resolve("scan.pdf"), "x");
        Path out = dir.resolve("custom.pdf");
        // input, direction=2(LTR), first-page=3(cover), despeckle n, register n,
        // (no deskew/scale prompts since register=n), pdf-a y, output=custom, start y.
        String script = in + "\n2\n3\nn\nn\ny\n" + out + "\ny\n";

        PipelineCommand.Plan plan = PipelineCommand.plan(scripted(script));

        assertThat(plan).isNotNull();
        assertThat(plan.output()).isEqualTo(out);
        PipelineCommand.Config c = plan.config();
        assertThat(c.direction()).isEqualTo(ReadingDirection.LTR);
        assertThat(c.firstPage()).isEqualTo(FirstPageMode.COVER);
        assertThat(c.despeckle()).isFalse();
        assertThat(c.register()).isFalse();
        assertThat(c.pdfA()).isTrue();
    }

    @Test
    void decliningStartReturnsNull(@TempDir Path dir) throws Exception {
        Path in = Files.writeString(dir.resolve("scan.pdf"), "x");
        // input + 8 defaults (dir, first-page, despeckle, register, deskew, scale, pdf-a, output),
        // then "Start conversion?" answered n.
        String script = in + "\n\n\n\n\n\n\n\n\nn\n";
        assertThat(PipelineCommand.plan(scripted(script))).isNull();
    }

    @Test
    void existingOutputDeclinedReturnsNull(@TempDir Path dir) throws Exception {
        Path in = Files.writeString(dir.resolve("scan.pdf"), "x");
        Path out = Files.writeString(dir.resolve("out.pdf"), "exists");
        // input + 7 defaults (dir, first-page, despeckle, register, deskew, scale, pdf-a),
        // output=existing, overwrite? n -> cancelled.
        String script = in + "\n\n\n\n\n\n\n\n" + out + "\nn\n";
        assertThat(PipelineCommand.plan(scripted(script))).isNull();
    }

    @Test
    void existingOutputOverwriteSetsForce(@TempDir Path dir) throws Exception {
        Path in = Files.writeString(dir.resolve("scan.pdf"), "x");
        Path out = Files.writeString(dir.resolve("out.pdf"), "exists");
        // input + 7 defaults, output=existing, overwrite? y, start y.
        String script = in + "\n\n\n\n\n\n\n\n" + out + "\ny\ny\n";
        PipelineCommand.Plan plan = PipelineCommand.plan(scripted(script));
        assertThat(plan).isNotNull();
        assertThat(plan.config().force()).isTrue();
    }
}
