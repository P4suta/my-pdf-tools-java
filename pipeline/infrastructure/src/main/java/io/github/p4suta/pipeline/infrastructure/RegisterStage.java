package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.diag.DiagnosticsReporterFactory;
import io.github.p4suta.register.infrastructure.registrar.LeptonicaPageRegistrar;
import io.github.p4suta.shared.kernel.PageProgressListener;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;

/**
 * The register {@link Stage}: deskews each page, derives a corpus reference layout, and places
 * every page onto a fixed paper-size canvas via register's {@link RegistrationService}, writing
 * registered TIFF-G4 pages. The paper size is auto-detected from the corpus; dpi is pinned to the
 * scan's resolution. Deskew and column-scaling are on by default (matching the register CLI).
 */
public final class RegisterStage implements Stage {

    /** register's OutputFormat.TIFF writes the {@code .tiff} extension. */
    private static final String OUTPUT_GLOB = "*.tiff";

    private static final double DEFAULT_OUTLIER_RATIO = 0.5;

    private final RegistrationService service;
    private final boolean deskew;
    private final boolean scale;
    private final int jobs;
    private final ProgressSink progress;

    /** {@link ProgressSink#NO_OP} variant of the four-arg constructor. */
    public RegisterStage(int jobs, boolean deskew, boolean scale) {
        this(jobs, deskew, scale, ProgressSink.NO_OP);
    }

    /**
     * @param jobs worker threads
     * @param deskew straighten each page before detection
     * @param scale scale each page's column to the reference height
     * @param progress sink that each placed page is reported into as a {@code PageProcessed} event
     *     (registration is two-pass, so progress spans {@code 2 * pageCount} units)
     */
    public RegisterStage(int jobs, boolean deskew, boolean scale, ProgressSink progress) {
        this.service =
                new RegistrationService(
                        new LeptonicaPageRegistrar(), new DiagnosticsReporterFactory());
        this.jobs = jobs;
        this.deskew = deskew;
        this.scale = scale;
        this.progress = progress;
    }

    @Override
    public String name() {
        return "register";
    }

    @Override
    public Corpus apply(Corpus input, Path workDir) throws IOException {
        RegisterOptions options =
                new RegisterOptions(
                        OptionalInt.of(input.dpi()),
                        null, // paper: auto-detect from the corpus median size
                        deskew,
                        scale,
                        DEFAULT_OUTLIER_RATIO,
                        Anchor.TOP_RIGHT);
        // Bridge the service's framework-free per-page callback into this stage's progress event,
        // labeling it with name() so it matches the StageStarted label PipelineRunner emits.
        PageProgressListener pages =
                (done, total) ->
                        progress.emit(new ProgressEvent.PageProcessed(name(), done, total));
        service.run(
                new RegistrationService.Config(
                        input.dir(),
                        workDir,
                        OutputFormat.TIFF,
                        input.glob(),
                        jobs,
                        true,
                        options,
                        null,
                        false),
                pages);
        return input.movedTo(workDir, OUTPUT_GLOB);
    }
}
