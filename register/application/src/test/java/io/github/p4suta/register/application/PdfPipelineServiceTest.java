package io.github.p4suta.register.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.application.Fakes.FakeJbig2Assembler;
import io.github.p4suta.register.application.Fakes.FakePageRegistrar;
import io.github.p4suta.register.application.Fakes.FakePdfImageExtractor;
import io.github.p4suta.register.application.Fakes.RecordingReporterFactory;
import io.github.p4suta.register.domain.exception.RegisterException;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.RegisterOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the PDF -> PDF pipeline driver over fake ports. The extract / register / assemble
 * stages are stubbed, so these pin the driver's own guards (missing input, output conflict, force
 * overwrite) and the dpi-resolution branch (explicit {@code --dpi} vs. the extractor's dominant
 * DPI), without pdfimages, jbig2 or PDFBox.
 */
class PdfPipelineServiceTest {

    private static RegisterOptions options(OptionalInt dpi) {
        return new RegisterOptions(dpi, null, true, true, 0.5, Anchor.TOP_RIGHT);
    }

    private PdfPipelineService service(
            FakePdfImageExtractor extractor, FakeJbig2Assembler assembler) {
        RegistrationService registration =
                new RegistrationService(
                        new FakePageRegistrar(true, 600, 2480, 3508),
                        new RecordingReporterFactory());
        return new PdfPipelineService(extractor, registration, assembler);
    }

    @Test
    void rejectsAMissingInputPdf(@TempDir Path tmp) {
        FakePdfImageExtractor extractor = new FakePdfImageExtractor(2, 600);
        FakeJbig2Assembler assembler = new FakeJbig2Assembler();
        PdfPipelineService service = service(extractor, assembler);
        PdfPipelineService.Config config =
                new PdfPipelineService.Config(
                        tmp.resolve("absent.pdf"),
                        tmp.resolve("out.pdf"),
                        options(OptionalInt.of(600)),
                        2,
                        false);

        assertThrows(RegisterException.class, () -> service.run(config));
        assertEquals(0, extractor.extractCalls.get());
    }

    @Test
    void rejectsAnExistingOutputWithoutForce(@TempDir Path tmp) throws IOException {
        Path input = Files.writeString(tmp.resolve("in.pdf"), "%PDF", StandardCharsets.UTF_8);
        Path output = Files.writeString(tmp.resolve("out.pdf"), "old", StandardCharsets.UTF_8);
        FakePdfImageExtractor extractor = new FakePdfImageExtractor(2, 600);
        PdfPipelineService service = service(extractor, new FakeJbig2Assembler());
        PdfPipelineService.Config config =
                new PdfPipelineService.Config(
                        input, output, options(OptionalInt.of(600)), 2, /* force= */ false);

        assertThrows(RegisterException.class, () -> service.run(config));
        assertEquals(0, extractor.extractCalls.get());
    }

    @Test
    void overwritesAnExistingOutputWithForce(@TempDir Path tmp) throws IOException {
        Path input = Files.writeString(tmp.resolve("in.pdf"), "%PDF", StandardCharsets.UTF_8);
        Path output = Files.writeString(tmp.resolve("out.pdf"), "old", StandardCharsets.UTF_8);
        FakePdfImageExtractor extractor = new FakePdfImageExtractor(2, 600);
        FakeJbig2Assembler assembler = new FakeJbig2Assembler();
        PdfPipelineService service = service(extractor, assembler);
        PdfPipelineService.Config config =
                new PdfPipelineService.Config(
                        input, output, options(OptionalInt.of(600)), 2, /* force= */ true);

        service.run(config);

        assertEquals(1, assembler.calls.get());
        assertTrue(Files.readString(output).startsWith("%PDF-fake"));
    }

    @Test
    void usesTheExplicitDpiWithoutProbingTheExtractor(@TempDir Path tmp) throws IOException {
        Path input = Files.writeString(tmp.resolve("in.pdf"), "%PDF", StandardCharsets.UTF_8);
        FakePdfImageExtractor extractor = new FakePdfImageExtractor(2, 600);
        FakeJbig2Assembler assembler = new FakeJbig2Assembler();
        PdfPipelineService service = service(extractor, assembler);
        PdfPipelineService.Config config =
                new PdfPipelineService.Config(
                        input, tmp.resolve("out.pdf"), options(OptionalInt.of(300)), 2, false);

        service.run(config);

        // Explicit --dpi=300 wins: dominantDpi is never consulted, and that dpi is forced on the
        // assembler.
        assertEquals(0, extractor.dominantDpiCalls.get());
        assertEquals(OptionalInt.of(300), assembler.lastForcedDpi);
    }

    @Test
    void fallsBackToTheExtractorsDominantDpiWhenNoneIsGiven(@TempDir Path tmp) throws IOException {
        Path input = Files.writeString(tmp.resolve("in.pdf"), "%PDF", StandardCharsets.UTF_8);
        FakePdfImageExtractor extractor = new FakePdfImageExtractor(2, 720);
        FakeJbig2Assembler assembler = new FakeJbig2Assembler();
        PdfPipelineService service = service(extractor, assembler);
        PdfPipelineService.Config config =
                new PdfPipelineService.Config(
                        input, tmp.resolve("out.pdf"), options(OptionalInt.empty()), 2, false);

        service.run(config);

        // No --dpi: the extractor's dominant DPI (720) is probed and forced through to the
        // assembler.
        assertEquals(1, extractor.dominantDpiCalls.get());
        assertEquals(OptionalInt.of(720), assembler.lastForcedDpi);
    }
}
