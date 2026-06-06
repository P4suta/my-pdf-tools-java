package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.despeckle.application.DespeckleService;
import io.github.p4suta.despeckle.domain.model.OutputFormat;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.infrastructure.leptonica.LeptonicaPageCleaner;
import io.github.p4suta.despeckle.infrastructure.report.HtmlReporterFactory;
import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.shared.kernel.PageProgressListener;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressSink;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;

/**
 * The despeckle {@link Stage}: removes scanner pepper-noise from the bitonal pages via despeckle's
 * {@link DespeckleService}, writing cleaned TIFF-G4 pages into the stage directory. The corpus dpi
 * is applied so the speck size is derived from the scan's true resolution. Conservative defaults
 * (fill pin-holes, drop isolated dust) match the despeckle CLI.
 */
public final class DespeckleStage implements Stage {

    /** despeckle's OutputFormat.TIFF writes the lowercase {@code .tif} extension. */
    private static final String OUTPUT_GLOB = "*.tif";

    private final DespeckleService service;
    private final ProcessOptions baseOptions;
    private final int jobs;
    private final ProgressSink progress;

    /**
     * @param jobs worker threads per book
     */
    public DespeckleStage(int jobs) {
        this(jobs, ProgressSink.NO_OP);
    }

    /**
     * @param jobs worker threads per book
     * @param progress sink that each cleaned page is reported into as a {@code PageProcessed} event
     */
    public DespeckleStage(int jobs, ProgressSink progress) {
        this.service = new DespeckleService(new LeptonicaPageCleaner(), new HtmlReporterFactory());
        this.jobs = jobs;
        this.baseOptions =
                new ProcessOptions(
                        OptionalInt.empty(), OptionalInt.empty(), true, true, OptionalInt.empty());
        this.progress = progress;
    }

    @Override
    public String name() {
        return "despeckle";
    }

    @Override
    public Corpus apply(Corpus input, Path workDir) throws IOException {
        // Bridge the service's framework-free per-page callback into this stage's progress event,
        // labelling it with name() so it matches the StageStarted label PipelineRunner emits.
        PageProgressListener pages =
                (done, total) ->
                        progress.emit(new ProgressEvent.PageProcessed(name(), done, total));
        service.run(
                new DespeckleService.Config(
                        input.dir(),
                        workDir,
                        OutputFormat.TIFF,
                        input.glob(),
                        jobs,
                        true,
                        baseOptions.withDpi(input.dpi()),
                        null,
                        false),
                pages);
        return input.movedTo(workDir, OUTPUT_GLOB);
    }
}
