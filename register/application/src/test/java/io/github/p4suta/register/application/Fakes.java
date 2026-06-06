package io.github.p4suta.register.application;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.Canvas;
import io.github.p4suta.register.domain.model.Detection;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PageAnalysis;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.domain.model.RunInfo;
import io.github.p4suta.register.domain.service.Reference;
import io.github.p4suta.register.port.Jbig2Assembler;
import io.github.p4suta.register.port.PageRegistrar;
import io.github.p4suta.register.port.PdfImageExtractor;
import io.github.p4suta.register.port.Reporter;
import io.github.p4suta.register.port.ReporterFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Hand-written fake adapters for the {@code :register:port} interfaces — no Mockito (it is not on
 * this module's classpath). Each fake produces just enough on-disk output for the next
 * orchestration stage to find, and records what it was asked to do, so {@link RegistrationService}
 * and {@link PdfPipelineService} can be unit-tested in isolation from {@code :infrastructure}.
 */
final class Fakes {

    private Fakes() {}

    /**
     * A {@link PageRegistrar} that writes a stub deskewed page on {@code analyze}, writes a stub
     * registered page on {@code renderPlaced}, and returns a fixed-shape analysis/diagnostic.
     *
     * <p>{@code detect} controls whether {@code analyze} reports a detected main column (so the
     * caller's reference-present vs. reference-null paths can both be reached); {@code scanDpi}
     * controls what {@code readScanResolution} reports (0 to model an input that carries none).
     */
    static class FakePageRegistrar implements PageRegistrar {
        final AtomicInteger analyzeCalls = new AtomicInteger();
        final AtomicInteger renderCalls = new AtomicInteger();
        private final boolean detect;
        private final int scanDpi;
        private final int width;
        private final int height;

        FakePageRegistrar(boolean detect, int scanDpi, int width, int height) {
            this.detect = detect;
            this.scanDpi = scanDpi;
            this.width = width;
            this.height = height;
        }

        @Override
        public int readScanResolution(Path file) {
            return scanDpi;
        }

        @Override
        public PageAnalysis analyze(
                Path source, Path deskewedScratch, RegisterOptions options, boolean recordSkew) {
            try {
                Files.writeString(deskewedScratch, "deskewed", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            analyzeCalls.incrementAndGet();
            Optional<Detection> detection =
                    detect
                            ? Optional.of(
                                    new Detection(
                                            new Box(10, 10, width / 2, height / 2),
                                            new io.github.p4suta.register.domain.model.Band(
                                                    10, 10 + height / 2)))
                            : Optional.empty();
            return new PageAnalysis(width, height, detection, null, 0);
        }

        @Override
        public PageDiagnostic renderPlaced(
                Path deskewedScratch,
                PageAnalysis analysis,
                int index,
                Parity parity,
                String source,
                @Nullable Reference reference,
                Canvas canvas,
                Path dest,
                OutputFormat format,
                RegisterOptions options) {
            try {
                Files.writeString(dest, "registered", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            renderCalls.incrementAndGet();
            return new PageDiagnostic(
                    index,
                    parity,
                    source,
                    analysis.width(),
                    analysis.height(),
                    canvas.width(),
                    canvas.height(),
                    options.deskew(),
                    null,
                    null,
                    reference == null ? null : reference.forParity(parity),
                    new PageDiagnostic.Placement(
                            analysis.detection().isPresent() && reference != null,
                            false,
                            1.0,
                            0,
                            0,
                            0,
                            0,
                            false,
                            false,
                            analysis.width(),
                            analysis.height()));
        }
    }

    /** A {@link Reporter} that counts {@code addPage}/{@code finish} calls. */
    static final class RecordingReporter implements Reporter {
        final AtomicInteger pages = new AtomicInteger();
        final AtomicInteger finished = new AtomicInteger();

        @Override
        public void addPage(PageDiagnostic diagnostic, Path deskewedPage) {
            pages.incrementAndGet();
        }

        @Override
        public void finish(RunInfo info, List<Path> outputs) {
            finished.incrementAndGet();
        }
    }

    /** A {@link ReporterFactory} that hands out a single {@link RecordingReporter}. */
    static final class RecordingReporterFactory implements ReporterFactory {
        final RecordingReporter reporter = new RecordingReporter();
        @Nullable Path createdFor;
        boolean createdFlipbook;

        @Override
        public Reporter create(Path diagDir, boolean flipbook) {
            createdFor = diagDir;
            createdFlipbook = flipbook;
            return reporter;
        }

        @Override
        public Reporter noOp() {
            return Reporter.noOp();
        }
    }

    /**
     * A {@link PdfImageExtractor} that emits {@code pages} stub {@code *.tif} files, optionally
     * failing the {@code extract} of any source whose file name contains {@code failOn} (so the
     * batch service's continue-on-error path can be exercised).
     */
    static final class FakePdfImageExtractor implements PdfImageExtractor {
        private final int pages;
        private final int dpi;
        private final @Nullable String failOn;
        final AtomicInteger dominantDpiCalls = new AtomicInteger();
        final AtomicInteger extractCalls = new AtomicInteger();

        FakePdfImageExtractor(int pages, int dpi) {
            this(pages, dpi, null);
        }

        FakePdfImageExtractor(int pages, int dpi, @Nullable String failOn) {
            this.pages = pages;
            this.dpi = dpi;
            this.failOn = failOn;
        }

        @Override
        public int dominantDpi(Path pdf) {
            dominantDpiCalls.incrementAndGet();
            return dpi;
        }

        @Override
        public void extract(Path pdf, Path outDir, int jobs, ExecutorService pool)
                throws IOException {
            extractCalls.incrementAndGet();
            if (failOn != null && pdf.getFileName().toString().contains(failOn)) {
                throw new IOException("fake extract failed for " + pdf);
            }
            for (int i = 1; i <= pages; i++) {
                Files.writeString(
                        outDir.resolve("page-%02d.tif".formatted(i)),
                        "tiff",
                        StandardCharsets.UTF_8);
            }
        }
    }

    /** A {@link Jbig2Assembler} that writes a stub PDF and records the forced DPI it was given. */
    static final class FakeJbig2Assembler implements Jbig2Assembler {
        final AtomicInteger calls = new AtomicInteger();
        OptionalInt lastForcedDpi = OptionalInt.empty();

        @Override
        public void assemble(
                Path imageDir,
                Path outPdf,
                @Nullable Path source,
                OptionalInt forcedDpi,
                ExecutorService pool,
                Path scratchDir)
                throws IOException {
            lastForcedDpi = forcedDpi;
            Files.writeString(outPdf, "%PDF-fake", StandardCharsets.UTF_8);
            calls.incrementAndGet();
        }
    }
}
