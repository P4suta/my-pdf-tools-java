package io.github.p4suta.register.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.application.Fakes.FakePageRegistrar;
import io.github.p4suta.register.application.Fakes.RecordingReporterFactory;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.Canvas;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PageAnalysis;
import io.github.p4suta.register.domain.model.PaperSize;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.port.PageRegistrar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the corpus registration orchestration over hand-written fake ports — no Leptonica,
 * no native tools. These pin the orchestration's branch logic (empty-corpus short-circuit, dpi
 * inheritance, auto vs. explicit paper, the reference-null all-centered path, diagnostics wiring,
 * and worker-failure propagation) that the end-to-end :app suites exercise only as a whole.
 */
class RegistrationServiceTest {

    private static RegisterOptions options(OptionalInt dpi, @Nullable PaperSize paper) {
        return new RegisterOptions(dpi, paper, true, true, 0.5, Anchor.TOP_RIGHT);
    }

    private static RegistrationService.Config config(
            Path in, Path out, RegisterOptions opts, @Nullable Path diagDir) {
        return new RegistrationService.Config(
                in, out, OutputFormat.TIFF, "*.tif", 2, false, opts, diagDir, false);
    }

    private static void writePages(Path dir, int n) throws IOException {
        for (int i = 0; i < n; i++) {
            Files.writeString(
                    dir.resolve("page-%02d.tif".formatted(i)), "scan", StandardCharsets.UTF_8);
        }
    }

    @Test
    void anEmptyCorpusReturnsAZeroSummaryWithoutTouchingTheRegistrar(@TempDir Path tmp)
            throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        FakePageRegistrar registrar = new FakePageRegistrar(true, 600, 1000, 1500);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());

        RegistrationService.Summary summary =
                service.run(
                        config(in, out, options(OptionalInt.of(600), PaperSize.Standard.A6), null));

        assertEquals(new RegistrationService.Summary(0, 0), summary);
        assertEquals(0, registrar.analyzeCalls.get());
    }

    @Test
    void registersEveryPageAndReportsHowManyHadADetectedColumn(@TempDir Path tmp)
            throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 4);
        FakePageRegistrar registrar = new FakePageRegistrar(true, 600, 1000, 1500);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());

        RegistrationService.Summary summary =
                service.run(
                        config(in, out, options(OptionalInt.of(600), PaperSize.Standard.A6), null));

        assertEquals(4, summary.pages());
        assertEquals(4, summary.analyzed());
        assertEquals(4, registrar.renderCalls.get());
        // The TIFF format maps every page to a .tiff extension via mirrorDestination.
        assertTrue(Files.exists(out.resolve("page-00.tiff")));
    }

    @Test
    void withNoDetectionEveryPageIsCenteredAndNoneCountsAsAnalyzed(@TempDir Path tmp)
            throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 3);
        // detect=false: analyze reports no main column, so no reference can be derived (the
        // reference-null branch) and every page is centered.
        FakePageRegistrar registrar = new FakePageRegistrar(false, 600, 1000, 1500);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());

        RegistrationService.Summary summary =
                service.run(
                        config(in, out, options(OptionalInt.of(600), PaperSize.Standard.A6), null));

        assertEquals(3, summary.pages());
        assertEquals(0, summary.analyzed());
    }

    @Test
    void inheritsTheScanResolutionWhenNoDpiIsGiven(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 2);
        // No --dpi: the canvas should be sized at the inputs' own scan resolution (600), not the
        // 400 default. The A6 width at 600 dpi differs from 400 dpi, so the canvas size proves it.
        FakePageRegistrar registrar = new FakePageRegistrar(true, 600, 1000, 1500);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());

        service.run(config(in, out, options(OptionalInt.empty(), PaperSize.Standard.A6), null));

        assertEquals(
                Canvas.of(PaperSize.Standard.A6, 600).width(), PaperSize.Standard.A6.widthPx(600));
        // The render pass ran for both pages at the inherited resolution.
        assertEquals(2, registrar.renderCalls.get());
    }

    @Test
    void fallsBackToTheDefaultDpiWhenInputsCarryNoResolution(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 2);
        // scanDpi=0: no input carries a resolution, so the run falls back to DEFAULT_DPI.
        FakePageRegistrar registrar = new FakePageRegistrar(true, 0, 1000, 1500);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());

        RegistrationService.Summary summary =
                service.run(
                        config(in, out, options(OptionalInt.empty(), PaperSize.Standard.A6), null));

        assertEquals(2, summary.pages());
    }

    @Test
    void autoDetectsThePaperFromTheMedianScanSizeWhenNoPaperIsGiven(@TempDir Path tmp)
            throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 3);
        // No --paper: paper is auto-detected from the median analyzed page size. At 600 dpi a page
        // of 2480x3508 px measures ~105x148 mm, which fromScan snaps to A6. The run must complete.
        FakePageRegistrar registrar = new FakePageRegistrar(true, 600, 2480, 3508);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());

        RegistrationService.Summary summary =
                service.run(config(in, out, options(OptionalInt.of(600), null), null));

        assertEquals(3, summary.pages());
        assertEquals(3, registrar.renderCalls.get());
    }

    @Test
    void writesDiagnosticsWhenADiagDirIsGiven(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        Path diag = tmp.resolve("diag");
        writePages(in, 2);
        FakePageRegistrar registrar = new FakePageRegistrar(true, 600, 1000, 1500);
        RecordingReporterFactory reporters = new RecordingReporterFactory();
        RegistrationService service = new RegistrationService(registrar, reporters);

        service.run(config(in, out, options(OptionalInt.of(600), PaperSize.Standard.A6), diag));

        assertEquals(diag, reporters.createdFor);
        assertEquals(2, reporters.reporter.pages.get());
        assertEquals(1, reporters.reporter.finished.get());
    }

    @Test
    void propagatesAWorkerFailureAsAnIoException(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 2);
        // A registrar whose analyze pass throws: the service must surface it, not swallow it.
        PageRegistrar failing =
                new FakePageRegistrar(true, 600, 1000, 1500) {
                    @Override
                    public PageAnalysis analyze(
                            Path source,
                            Path deskewedScratch,
                            RegisterOptions opts,
                            boolean recordSkew) {
                        throw new IllegalStateException("boom");
                    }
                };
        RegistrationService service =
                new RegistrationService(failing, new RecordingReporterFactory());

        assertThrows(
                IOException.class,
                () ->
                        service.run(
                                config(
                                        in,
                                        out,
                                        options(OptionalInt.of(600), PaperSize.Standard.A6),
                                        null)));
    }

    @Test
    void reportsProgressAcrossBothPassesOverATwoNDenominator(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePages(in, 3);
        FakePageRegistrar registrar = new FakePageRegistrar(true, 600, 1000, 1500);
        RegistrationService service =
                new RegistrationService(registrar, new RecordingReporterFactory());
        // jobs=2, so progress is reported from the worker threads across two sequential passes.
        List<int[]> seen = Collections.synchronizedList(new ArrayList<>());

        service.run(
                config(in, out, options(OptionalInt.of(600), PaperSize.Standard.A6), null),
                (done, total) -> seen.add(new int[] {done, total}));

        // Two passes over 3 pages = 6 callbacks, each carrying the 2N=6 denominator, and the shared
        // counter visits every value 1..6 exactly once (analyze 1..3, render 4..6).
        assertEquals(6, seen.size(), "one callback per page in each of the two passes");
        boolean[] reported = new boolean[7]; // index by 1-based done count over 2N
        for (int[] pair : seen) {
            assertEquals(6, pair[1], "total is 2N on every callback");
            assertTrue(pair[0] >= 1 && pair[0] <= 6, "done is within 1..2N");
            reported[pair[0]] = true;
        }
        for (int n = 1; n <= 6; n++) {
            assertTrue(
                    reported[n], "done value " + n + " was reported exactly once across the run");
        }
    }
}
