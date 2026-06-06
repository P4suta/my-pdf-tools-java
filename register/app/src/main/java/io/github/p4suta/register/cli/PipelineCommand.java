package io.github.p4suta.register.cli;

import io.github.p4suta.register.application.PdfBatchService;
import io.github.p4suta.register.application.PdfPipelineService;
import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.diag.DiagnosticsReporterFactory;
import io.github.p4suta.register.infrastructure.pdf.PdfBoxJbig2Assembler;
import io.github.p4suta.register.infrastructure.pdf.PdfImagesCliExtractor;
import io.github.p4suta.register.infrastructure.registrar.LeptonicaPageRegistrar;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Front end for {@code register pipeline <input.pdf> <output.pdf>}: register a scanned PDF onto a
 * fixed paper-size canvas and repack it as a lossless-JBIG2 PDF in one step. Shares the
 * registration options (and their parsers) with {@link RegisterCommand}; like it, this is allowed
 * to write to {@code System.out}/{@code System.err}.
 */
final class PipelineCommand {

    private static final String SYNTAX =
            "register pipeline [options] <input.pdf> <output.pdf> | <input-dir> <output-dir>";

    private static final String HEADER =
            "Register a scanned PDF onto a fixed paper-size canvas and repack it as a"
                + " lossless-JBIG2 PDF (pdfimages -> register -> jbig2), all in one self-contained"
                + " step. With two files, <input.pdf> is the source scan and <output.pdf> the"
                + " registered result. With a directory as the first argument, every top-level"
                + " *.pdf under <input-dir> is registered into <output-dir>/<same-name>.pdf"
                + " (existing outputs are skipped unless --force; one failed book never stops the"
                + " rest).";

    private static final Options OPTIONS = buildOptions();

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(
                Option.builder("h").longOpt("help").desc("Show this help and exit.").get());
        options.addOption(
                Option.builder()
                        .longOpt("paper")
                        .hasArg()
                        .argName("size")
                        .desc(
                                "Target paper size: auto (default), a standard name (shiroku, a4,"
                                    + " a5, a6, b5, b6, shinsho), or a custom WxH in millimeters.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("dpi")
                        .hasArg()
                        .argName("n")
                        .desc("Output resolution; default: the source scan's dominant resolution.")
                        .get());
        options.addOption(
                Option.builder("j")
                        .longOpt("jobs")
                        .hasArg()
                        .argName("n")
                        .desc("Worker threads (default: available processors).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("force")
                        .desc(
                                "Overwrite an existing output PDF; in batch mode, regenerate"
                                        + " outputs that already exist instead of skipping them.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("suffix")
                        .hasArg()
                        .argName("s")
                        .desc(
                                "Batch mode: insert <s> before each output's .pdf extension"
                                        + " (e.g. --suffix _registered writes book.pdf ->"
                                        + " book_registered.pdf).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-deskew")
                        .desc("Do not straighten each page before detection.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-scale")
                        .desc("Do not scale each page's column to the reference height.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("outlier-ratio")
                        .hasArg()
                        .argName("r")
                        .desc(
                                "A column smaller than this fraction of the reference area is"
                                        + " centered, not registered (default: 0.5).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("anchor")
                        .hasArg()
                        .argName("where")
                        .desc("Where to register the column: center or top_right (default).")
                        .get());
        return options;
    }

    int execute(String[] args) {
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            return usageError(e);
        }
        if (cmd.hasOption("help")) {
            printHelp();
            return ExitCodes.OK;
        }

        Parsed parsed;
        try {
            parsed = toParsed(cmd);
        } catch (ParseException | IllegalArgumentException e) {
            return usageError(e);
        }

        try {
            // A directory input batches every top-level *.pdf into the output directory; a file
            // input is the single-PDF pipeline. Both share the registration options above; --suffix
            // only shapes batch output names (single mode names its output explicitly).
            PdfPipelineService pipeline = pdfPipelineService();
            if (PdfBatchService.isBatchInput(parsed.input())) {
                PdfBatchService.Summary summary =
                        new PdfBatchService(pipeline)
                                .run(
                                        new PdfBatchService.Config(
                                                parsed.input(),
                                                parsed.output(),
                                                parsed.options(),
                                                parsed.jobs(),
                                                parsed.force(),
                                                parsed.suffix()));
                // Continue-on-error batch: per-book failures are counted, not a single
                // ErrorCategory, so a partial failure surfaces as the generic internal /
                // software-failure code (EX_SOFTWARE, 70).
                return summary.failed() > 0 ? ExitCodes.INTERNAL : ExitCodes.OK;
            }
            pipeline.run(
                    new PdfPipelineService.Config(
                            parsed.input(),
                            parsed.output(),
                            parsed.options(),
                            parsed.jobs(),
                            parsed.force()));
            return ExitCodes.OK;
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> false).handle(e);
        }
    }

    /**
     * The PDF pipeline service wired to its infrastructure adapters (pdfimages extractor, Leptonica
     * registrar, diagnostics reporter factory, PDFBox JBIG2 assembler). This {@code cli} package is
     * the composition root that assembles the application services from the adapters.
     */
    private static PdfPipelineService pdfPipelineService() {
        RegistrationService registration =
                new RegistrationService(
                        new LeptonicaPageRegistrar(), new DiagnosticsReporterFactory());
        return new PdfPipelineService(
                new PdfImagesCliExtractor(), registration, new PdfBoxJbig2Assembler());
    }

    /**
     * Parsed pipeline arguments.
     *
     * @param input the source PDF, or directory of PDFs in batch mode
     * @param output the output PDF, or directory in batch mode
     * @param suffix the batch-mode output-name suffix ({@code ""} when none)
     */
    record Parsed(
            Path input,
            Path output,
            RegisterOptions options,
            int jobs,
            boolean force,
            String suffix) {}

    /**
     * Parse {@code args} into a {@link Parsed} without running. Package-private so the pipeline CLI
     * parsing can be unit-tested, mirroring {@code RegisterCommand.parse}.
     *
     * @param args the raw arguments (after the {@code pipeline} subcommand)
     * @throws ParseException if the options or positionals are malformed
     */
    Parsed parse(String[] args) throws ParseException {
        return toParsed(new DefaultParser().parse(OPTIONS, args));
    }

    private static Parsed toParsed(CommandLine cmd) throws ParseException {
        List<String> positionals =
                CliOptionSupport.requireNPositionals(cmd, 2, "<input>", "<output>");
        return new Parsed(
                Path.of(positionals.get(0)),
                Path.of(positionals.get(1)),
                CliSupport.buildRegisterOptions(cmd),
                Math.max(1, CliOptionSupport.parseInt(cmd, "jobs", availableProcessors())),
                cmd.hasOption("force"),
                cmd.getOptionValue("suffix", ""));
    }

    private static int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    private int usageError(Exception cause) {
        return CliOptionSupport.usageError("register", SYNTAX, "register pipeline --help", cause);
    }

    private void printHelp() {
        CliOptionSupport.printHelp("register", SYNTAX, HEADER, OPTIONS);
    }
}
