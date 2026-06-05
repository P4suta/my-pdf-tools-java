package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.despeckle.application.DespeckleService;
import io.github.p4suta.despeckle.domain.model.OutputFormat;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.infrastructure.leptonica.LeptonicaPageCleaner;
import io.github.p4suta.despeckle.infrastructure.report.HtmlReporterFactory;
import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Stage;
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

    /**
     * @param jobs worker threads per book
     */
    public DespeckleStage(int jobs) {
        this.service = new DespeckleService(new LeptonicaPageCleaner(), new HtmlReporterFactory());
        this.jobs = jobs;
        this.baseOptions =
                new ProcessOptions(
                        OptionalInt.empty(), OptionalInt.empty(), true, true, OptionalInt.empty());
    }

    @Override
    public String name() {
        return "despeckle";
    }

    @Override
    public Corpus apply(Corpus input, Path workDir) throws IOException {
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
                        false));
        return input.movedTo(workDir, OUTPUT_GLOB);
    }
}
