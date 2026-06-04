package io.github.p4suta.register;

import io.github.p4suta.register.application.PdfBatchService;
import io.github.p4suta.register.application.PdfPipelineService;
import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.infrastructure.diag.DiagnosticsReporterFactory;
import io.github.p4suta.register.infrastructure.pdf.PdfBoxJbig2Assembler;
import io.github.p4suta.register.infrastructure.pdf.PdfImagesCliExtractor;
import io.github.p4suta.register.infrastructure.registrar.LeptonicaPageRegistrar;

/**
 * Test-only composition root: assembles the application services from their real infrastructure
 * adapters, mirroring what the CLI front ends wire at run time. The end-to-end tests drive the live
 * Leptonica / PDFBox / pdfimages-jbig2 stack through these, so they exercise the same graph the
 * shipped app does.
 */
public final class TestComposition {

    private TestComposition() {}

    /** A registration service wired to the Leptonica registrar and diagnostics reporter factory. */
    public static RegistrationService registrationService() {
        return new RegistrationService(
                new LeptonicaPageRegistrar(), new DiagnosticsReporterFactory());
    }

    /** A PDF -> PDF pipeline service wired to all of its adapters. */
    public static PdfPipelineService pdfPipelineService() {
        return new PdfPipelineService(
                new PdfImagesCliExtractor(), registrationService(), new PdfBoxJbig2Assembler());
    }

    /** A batch service over the wired single-PDF pipeline. */
    public static PdfBatchService pdfBatchService() {
        return new PdfBatchService(pdfPipelineService());
    }
}
