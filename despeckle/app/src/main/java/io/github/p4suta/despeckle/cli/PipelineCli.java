package io.github.p4suta.despeckle.cli;

import io.github.p4suta.despeckle.application.DespeckleService;
import io.github.p4suta.despeckle.application.PdfBatchService;
import io.github.p4suta.despeckle.application.PdfPipelineService;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.infrastructure.leptonica.LeptonicaPageCleaner;
import io.github.p4suta.despeckle.infrastructure.pdf.PdfBoxJbig2Assembler;
import io.github.p4suta.despeckle.infrastructure.pdf.PdfImagesCliExtractor;
import io.github.p4suta.despeckle.infrastructure.pdf.QpdfLinearizer;
import io.github.p4suta.despeckle.infrastructure.report.HtmlBatchReporter;
import io.github.p4suta.despeckle.infrastructure.report.HtmlReporterFactory;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Front end for {@code despeckle pipeline <in.pdf> <out.pdf>}: clean a scanned PDF end-to-end
 * (pdfimages → despeckle → lossless JBIG2) in one self-contained step. A directory as the first
 * argument batches every top-level {@code *.pdf} into the output directory. It shares the despeckle
 * clean knobs (and their {@link ProcessOptions} wiring) with {@link DespeckleCli}; like it, this is
 * the only other class allowed to write to {@code System.out}/{@code System.err}, and it returns
 * the same exit-code contract (0 success, 2 usage, 1 runtime / any batch failure).
 */
final class PipelineCli {

    private static final String SYNTAX =
            "despeckle pipeline <in.pdf> <out.pdf> | <in-dir> <out-dir> [options]";
    private static final String DESCRIPTION =
            "Clean a scanned PDF end-to-end (pdfimages -> despeckle -> lossless JBIG2), all in one"
                + " self-contained step. With two files, <in.pdf> is the source scan and <out.pdf>"
                + " the cleaned result. With a directory as the first argument, every top-level"
                + " *.pdf under <in-dir> is cleaned into <out-dir>/<same-name>.pdf (existing"
                + " outputs are skipped unless --force; one failed book never stops the rest).";

    private final Options options = buildOptions();

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(
                Option.builder("h")
                        .longOpt(DespeckleOptions.HELP)
                        .desc("Show this help and exit.")
                        .get());
        options.addOption(
                Option.builder("j")
                        .longOpt(DespeckleOptions.JOBS)
                        .hasArg()
                        .argName("N")
                        .desc("Worker threads per book (default: available processors).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.FORCE)
                        .desc(
                                "Overwrite an existing output PDF; in batch, regenerate existing"
                                        + " ones.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.SUFFIX)
                        .hasArg()
                        .argName("S")
                        .desc(
                                "Batch: insert <S> before each output's .pdf (e.g. --suffix _clean"
                                        + " writes book.pdf -> book_clean.pdf).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.REPORT)
                        .hasArg()
                        .argName("DIR")
                        .desc(
                                "Write a per-book HTML report here (in batch, a top-level"
                                    + " index.html links each book's report under <DIR>/<name>/).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.FLIPBOOK)
                        .desc(
                                "With --report, also assemble the overlay flip-book (needs"
                                        + " img2webp).")
                        .get());
        DespeckleOptions.addCleanKnobs(options);
        return options;
    }

    /** Parse {@code args} (everything after {@code pipeline}), run, and return the exit code. */
    int run(String[] args) {
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            return usageError(e);
        }
        if (cmd.hasOption(DespeckleOptions.HELP)) {
            CliOptionSupport.printHelp("despeckle pipeline", SYNTAX, DESCRIPTION, options);
            return ExitCodes.OK;
        }

        // The split mirrors DespeckleCli: usage/type problems are the usage exit 2
        // (ParseException),
        // while value validation (ProcessOptions rejects a non-positive size) and pipeline I/O are
        // mapped to their sysexits codes by the shared ExceptionMapper.
        try {
            return dispatch(parseArgs(cmd));
        } catch (ParseException e) {
            return usageError(e);
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> false).handle(e);
        }
    }

    private int dispatch(Parsed parsed) throws IOException {
        DespeckleService despeckleService =
                new DespeckleService(new LeptonicaPageCleaner(), new HtmlReporterFactory());
        PdfPipelineService pipeline =
                new PdfPipelineService(
                        new PdfImagesCliExtractor(),
                        despeckleService,
                        new PdfBoxJbig2Assembler(),
                        new QpdfLinearizer());
        if (Files.isDirectory(parsed.input())) {
            PdfBatchService.Summary summary =
                    new PdfBatchService(pipeline, new HtmlBatchReporter())
                            .run(
                                    new PdfBatchService.Config(
                                            parsed.input(),
                                            parsed.output(),
                                            parsed.options(),
                                            parsed.jobs(),
                                            parsed.force(),
                                            parsed.suffix(),
                                            parsed.reportDir(),
                                            parsed.flipbook()));
            // Continue-on-error batch: individual books are logged with their own kind as they
            // fail; the run as a whole reports the EX_SOFTWARE aggregate (70) when any book failed.
            return summary.failed() > 0 ? ExitCodes.INTERNAL : ExitCodes.OK;
        }
        pipeline.run(
                new PdfPipelineService.Config(
                        parsed.input(),
                        parsed.output(),
                        parsed.options(),
                        parsed.jobs(),
                        parsed.force(),
                        parsed.reportDir(),
                        parsed.flipbook()));
        return ExitCodes.OK;
    }

    /** Type-converts and validates the parsed command line; failures here are usage errors. */
    private Parsed parseArgs(CommandLine cmd) throws ParseException {
        List<String> positionals = CliOptionSupport.requireNPositionals(cmd, 2, "<in>", "<out>");
        Path input = Path.of(positionals.get(0));
        Path output = Path.of(positionals.get(1));

        Path reportDir =
                cmd.hasOption(DespeckleOptions.REPORT)
                        ? Path.of(cmd.getOptionValue(DespeckleOptions.REPORT))
                        : null;
        boolean flipbook = cmd.hasOption(DespeckleOptions.FLIPBOOK);
        if (flipbook && reportDir == null) {
            throw new ParseException("--flipbook needs --report");
        }

        int jobs =
                CliOptionSupport.parseInt(
                        cmd, DespeckleOptions.JOBS, Runtime.getRuntime().availableProcessors());
        boolean force = cmd.hasOption(DespeckleOptions.FORCE);
        String suffix = cmd.getOptionValue(DespeckleOptions.SUFFIX, "");

        ProcessOptions options = DespeckleOptions.cleanProcessOptions(cmd);
        return new Parsed(
                input, output, Math.max(1, jobs), force, suffix, reportDir, flipbook, options);
    }

    private int usageError(ParseException cause) {
        return CliOptionSupport.usageError(
                "despeckle pipeline", SYNTAX, "despeckle pipeline --help", cause);
    }

    /** Parsed, type-converted pipeline command line. */
    private record Parsed(
            Path input,
            Path output,
            int jobs,
            boolean force,
            String suffix,
            @Nullable Path reportDir,
            boolean flipbook,
            ProcessOptions options) {}
}
