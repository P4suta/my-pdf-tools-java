package io.github.p4suta.register.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.TestComposition;
import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PaperSize;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.TestImages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end for the {@code --diag --flipbook} artifacts. The corpus before/after overlay and the
 * residual chart are written on every {@code --diag} run; the WebP flip-book is written only when
 * libwebp's {@code img2webp} is on the PATH. Crucially, a missing {@code img2webp} must degrade
 * gracefully — the run still succeeds and the other artifacts are still produced.
 */
class FlipbookE2eTest {

    @Test
    void writesCorpusArtifactsAndFlipbookWhenToolPresent(@TempDir Path tmp) throws Exception {
        Path input = tmp.resolve("in");
        Path output = tmp.resolve("out");
        Path diag = tmp.resolve("diag");
        Files.createDirectories(input);

        int[][] columnX = {{38, 68}, {30, 60}, {40, 70}, {32, 62}};
        for (int i = 0; i < columnX.length; i++) {
            boolean[][] page =
                    TestImages.pageWithColumn(100, 140, columnX[i][0], 10, columnX[i][1], 130);
            TestImages.writePbm(input.resolve("page-%02d.pbm".formatted(i + 1)), page);
        }

        RegisterOptions options =
                new RegisterOptions(
                        OptionalInt.of(100),
                        PaperSize.Standard.A6,
                        false,
                        true,
                        0.5,
                        Anchor.TOP_RIGHT);
        RegistrationService.Config config =
                new RegistrationService.Config(
                        input, output, OutputFormat.PNG, "*.pbm", 2, false, options, diag, true);

        // The run must succeed whether or not img2webp is installed (graceful degradation).
        TestComposition.registrationService().run(config);

        assertTrue(
                Files.exists(diag.resolve("corpus-overlay.png")),
                "corpus overlay written on --diag");
        assertTrue(Files.exists(diag.resolve("residuals.png")), "residual chart written on --diag");
        // No stray frame scratch directory is left behind.
        try (Stream<Path> entries = Files.list(diag)) {
            assertTrue(
                    entries.noneMatch(p -> p.getFileName().toString().startsWith(".flipbook")),
                    "flip-book frame scratch directory cleaned up");
        }

        if (img2webpAvailable()) {
            assertTrue(
                    Files.exists(diag.resolve("flipbook.webp")),
                    "flip-book written when img2webp is present");
        }
    }

    private static boolean img2webpAvailable() {
        try {
            Process p =
                    new ProcessBuilder("img2webp", "-version")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
