package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.p4suta.tateyokopdf.application.SpreadOptions;
import io.github.p4suta.tateyokopdf.application.SpreadService;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.infrastructure.qpdf.QpdfLinearizer;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import io.github.p4suta.tateyokopdf.testfixtures.CapturingProgressListener;
import io.github.p4suta.tateyokopdf.testfixtures.PdfFixtures;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/**
 * End-to-end check that {@code --pdf-a} output genuinely validates as PDF/A-2b, using the veraPDF
 * greenfield validator (an independent PDF parser, so it cannot share a blind spot with PDFBox).
 *
 * <p>The fixture is a font-free blank PDF: because the spread tool embeds source pages verbatim,
 * full conformance is only reachable when the source itself carries nothing PDF/A forbids (no
 * unembedded fonts, no device color without an output intent).
 */
final class PdfAConformanceTest {

    @Test
    void pdfAOutputValidatesAsPdfA2b(@TempDir Path tmp) throws Exception {
        // noOp post-processor isolates the structure PDFBox emits from the downstream qpdf step.
        Path output = convert(tmp, PdfPostProcessor.noOp());
        assertCompliant(output);
    }

    @Test
    void pdfAOutputStaysCompliantAfterQpdfLinearization(@TempDir Path tmp) throws Exception {
        assumeTrue(qpdfOnPath(), "qpdf not on PATH; skipping linearized PDF/A check");
        // The shipping pipeline always linearizes via qpdf — verify that does not strip the
        // output intent / XMP or otherwise break conformance.
        Path output = convert(tmp, QpdfLinearizer.create());
        assertCompliant(output);
    }

    private static Path convert(Path tmp, PdfPostProcessor postProcessor) throws Exception {
        Path input = PdfFixtures.blankPages(tmp, "in.pdf", 4);
        Path output = tmp.resolve("out.pdf");
        var service =
                new SpreadService(
                        new PdfBoxDocumentFactory(),
                        new SpreadLayoutCalculator(),
                        postProcessor,
                        new CapturingProgressListener());
        service.execute(
                new SpreadOptions(
                        input, output, ReadingDirection.RTL, FirstPageMode.STANDARD, true));
        return output;
    }

    private static void assertCompliant(Path pdf) throws Exception {
        VeraGreenfieldFoundryProvider.initialise();
        PDFAFlavour flavor = PDFAFlavour.PDFA_2_B;
        ValidationResult result;
        try (InputStream in = Files.newInputStream(pdf);
                PDFAParser parser = Foundries.defaultInstance().createParser(in, flavor);
                PDFAValidator validator =
                        Foundries.defaultInstance().createValidator(flavor, false)) {
            result = validator.validate(parser);
        }
        String failures =
                result.getTestAssertions().stream()
                        .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                        .map(a -> a.getRuleId() + " — " + a.getMessage())
                        .distinct()
                        .collect(Collectors.joining(System.lineSeparator()));
        assertThat(result.isCompliant())
                .as("expected PDF/A-2b compliance, veraPDF failures:%n%s", failures)
                .isTrue();
    }

    // Same trade-off as QpdfLinearizer#resolveOnPath: pulling in Guava's Splitter for one PATH walk
    // in a test guard is not worth it, and the empty-entry edge case is handled explicitly.
    @SuppressWarnings("StringSplitter")
    private static boolean qpdfOnPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, "qpdf"))) {
                return true;
            }
        }
        return false;
    }
}
