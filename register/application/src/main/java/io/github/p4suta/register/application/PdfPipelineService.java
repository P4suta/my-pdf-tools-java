package io.github.p4suta.register.application;

import io.github.p4suta.register.domain.exception.RegisterErrorKind;
import io.github.p4suta.register.domain.exception.RegisterException;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.port.Jbig2Assembler;
import io.github.p4suta.register.port.PdfImageExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The end-to-end PDF -> PDF driver: extract the source PDF's bitonal images (the {@link
 * PdfImageExtractor} port over {@code pdfimages}), register them onto the fixed canvas (the corpus
 * {@link RegistrationService}), and repack the registered pages as a lossless-JBIG2 PDF (the {@link
 * Jbig2Assembler} port over PDFBox + {@code jbig2}). All intermediates live under one temp
 * directory removed at the end, so the only inputs/outputs are the two PDFs.
 *
 * <p>The three steps are reached only through injected ports (and the injected registration
 * service), so this service stays free of {@code :infrastructure}.
 */
public final class PdfPipelineService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfPipelineService.class);

    private final PdfImageExtractor extractor;
    private final RegistrationService registrationService;
    private final Jbig2Assembler assembler;

    public PdfPipelineService(
            PdfImageExtractor extractor,
            RegistrationService registrationService,
            Jbig2Assembler assembler) {
        this.extractor = extractor;
        this.registrationService = registrationService;
        this.assembler = assembler;
    }

    /**
     * One PDF -> PDF run.
     *
     * @param options registration knobs (its empty {@code --dpi} is resolved from the scan)
     */
    public record Config(
            Path inputPdf, Path outputPdf, RegisterOptions options, int jobs, boolean force) {}

    /**
     * Run the pipeline.
     *
     * @throws IOException on a missing input, a failed external tool, or a write failure
     */
    public void run(Config config) throws IOException {
        if (!Files.isRegularFile(config.inputPdf())) {
            throw RegisterException.withDetail(
                    RegisterErrorKind.INPUT_NOT_FOUND,
                    "input PDF not found: " + config.inputPdf(),
                    null);
        }
        Path outParent = config.outputPdf().toAbsolutePath().getParent();
        if (outParent != null) {
            Files.createDirectories(outParent);
        }
        if (!config.force() && Files.exists(config.outputPdf())) {
            throw RegisterException.withDetail(
                    RegisterErrorKind.OUTPUT_CONFLICT,
                    config.outputPdf() + " already exists; pass --force to overwrite",
                    null);
        }

        Path work = createBeside(config.outputPdf(), ".register-pipeline-");
        try {
            Path extracted = Files.createDirectories(work.resolve("in"));
            Path registered = Files.createDirectories(work.resolve("reg"));
            Path jbig2Dir = Files.createDirectories(work.resolve("jb2"));
            int dpi =
                    config.options().dpi().isPresent()
                            ? config.options().dpi().getAsInt()
                            : extractor.dominantDpi(config.inputPdf());
            LOG.info("pipeline: {} -> {} at {} dpi", config.inputPdf(), config.outputPdf(), dpi);

            // Each step fans out on its own batch-owned workers bounded by the same jobs budget,
            // so the steps never hold idle threads for each other (the old shared outer pool sat
            // idle through the whole registration step).
            extractor.extract(config.inputPdf(), extracted, config.jobs());

            RegistrationService.Config registration =
                    new RegistrationService.Config(
                            extracted,
                            registered,
                            OutputFormat.TIFF,
                            "*.tif",
                            config.jobs(),
                            true,
                            withDpi(config.options(), dpi),
                            null,
                            false);
            registrationService.run(registration);

            assembler.assemble(
                    registered,
                    config.outputPdf(),
                    config.inputPdf(),
                    OptionalInt.of(dpi),
                    config.jobs(),
                    jbig2Dir);
            LOG.info("wrote {}", config.outputPdf());
        } finally {
            deleteRecursively(work);
        }
    }

    /** The registration step needs an explicit dpi: the user's {@code --dpi}, else the scan's. */
    private static RegisterOptions withDpi(RegisterOptions options, int dpi) {
        return new RegisterOptions(
                OptionalInt.of(dpi),
                options.paper(),
                options.deskew(),
                options.scale(),
                options.outlierRatio(),
                options.anchor());
    }

    private static Path createBeside(Path sibling, String prefix) throws IOException {
        Path parent = sibling.toAbsolutePath().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            return Files.createTempDirectory(parent, prefix);
        }
        return Files.createTempDirectory(prefix);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(PdfPipelineService::deleteQuietly);
        } catch (IOException e) {
            LOG.warn("could not clean up pipeline temp dir {}: {}", dir, e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("could not delete {}: {}", path, e.getMessage());
        }
    }
}
