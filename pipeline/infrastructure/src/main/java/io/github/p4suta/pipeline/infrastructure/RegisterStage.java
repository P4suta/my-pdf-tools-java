package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.diag.DiagnosticsReporterFactory;
import io.github.p4suta.register.infrastructure.registrar.LeptonicaPageRegistrar;
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

    /**
     * @param jobs worker threads
     * @param deskew straighten each page before detection
     * @param scale scale each page's column to the reference height
     */
    public RegisterStage(int jobs, boolean deskew, boolean scale) {
        this.service =
                new RegistrationService(
                        new LeptonicaPageRegistrar(), new DiagnosticsReporterFactory());
        this.jobs = jobs;
        this.deskew = deskew;
        this.scale = scale;
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
                        false));
        return input.movedTo(workDir, OUTPUT_GLOB);
    }
}
