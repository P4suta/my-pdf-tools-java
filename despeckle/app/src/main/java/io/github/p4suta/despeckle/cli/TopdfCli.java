package io.github.p4suta.despeckle.cli;

import io.github.p4suta.despeckle.application.Jbig2PackService;
import io.github.p4suta.despeckle.infrastructure.pdf.PdfBoxJbig2Assembler;
import io.github.p4suta.despeckle.infrastructure.pdf.QpdfLinearizer;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliLogging;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.cli.CliVersion;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Front end for {@code despeckle topdf <image-dir> <out.pdf>}: pack a directory of already-cleaned
 * bitonal pages into one lossless-JBIG2 PDF — the tail of the image-mode flow ({@code despeckle
 * <in> <out>} then {@code topdf}). Each page keeps its own resolution unless {@code --dpi} forces
 * one; {@code --source} inherits a scan's metadata. Like {@link DespeckleCli} / {@link PipelineCli}
 * it owns the standard streams and the same exit-code contract (0 success, 2 usage, 1 runtime).
 */
final class TopdfCli {

    private static final String SYNTAX = "despeckle topdf <image-dir> <out.pdf> [options]";
    private static final String DESCRIPTION =
            "Pack a directory of cleaned bitonal pages into one lossless-JBIG2 PDF (jbig2 + qpdf"
                    + " --linearize), the pure-Java repack stage of the image-mode flow. Each page"
                    + " keeps its own resolution unless --dpi forces one.";

    private final Options options = buildOptions();

    private static Options buildOptions() {
        Options options = new Options();
        DespeckleOptions.addStandardFlags(options);
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.DPI)
                        .hasArg()
                        .argName("N")
                        .desc(
                                "Force a single page-size resolution; default: each image's own"
                                        + " tag, else 300.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.SOURCE)
                        .hasArg()
                        .argName("PDF")
                        .desc("Inherit Info/XMP metadata and PDF version from this source scan.")
                        .get());
        options.addOption(
                Option.builder("j")
                        .longOpt(DespeckleOptions.JOBS)
                        .hasArg()
                        .argName("N")
                        .desc("Worker threads (default: available processors).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt(DespeckleOptions.FORCE)
                        .desc("Overwrite an existing output PDF.")
                        .get());
        return options;
    }

    /** Parse {@code args} (everything after {@code topdf}), run, and return the exit code. */
    int run(String[] args) {
        // A bare `despeckle topdf` prints help and succeeds rather than erroring on missing args.
        if (args.length == 0) {
            CliOptionSupport.printHelp("despeckle topdf", SYNTAX, DESCRIPTION, options);
            return ExitCodes.OK;
        }
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            return usageError(e);
        }
        boolean verbose = cmd.hasOption(DespeckleOptions.VERBOSE);
        if (verbose) {
            CliLogging.enableDebug();
        }
        if (cmd.hasOption(DespeckleOptions.HELP)) {
            CliOptionSupport.printHelp("despeckle topdf", SYNTAX, DESCRIPTION, options);
            return ExitCodes.OK;
        }
        if (cmd.hasOption(DespeckleOptions.VERSION)) {
            System.out.println(CliVersion.line("despeckle", TopdfCli.class));
            return ExitCodes.OK;
        }
        try {
            new Jbig2PackService(new PdfBoxJbig2Assembler(), new QpdfLinearizer())
                    .run(parseArgs(cmd));
            return ExitCodes.OK;
        } catch (ParseException e) {
            return usageError(e);
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> verbose).handle(e);
        }
    }

    private Jbig2PackService.Config parseArgs(CommandLine cmd) throws ParseException {
        List<String> positionals =
                CliOptionSupport.requireNPositionals(cmd, 2, "<image-dir>", "<out.pdf>");
        Path imageDir = Path.of(positionals.get(0));
        Path outPdf = Path.of(positionals.get(1));

        @Nullable Path source =
                cmd.hasOption(DespeckleOptions.SOURCE)
                        ? Path.of(cmd.getOptionValue(DespeckleOptions.SOURCE))
                        : null;

        OptionalInt dpi = OptionalInt.empty();
        if (cmd.hasOption(DespeckleOptions.DPI)) {
            int value = CliOptionSupport.parseInt(cmd, DespeckleOptions.DPI, 0);
            if (value <= 0) {
                throw new IllegalArgumentException("--dpi must be positive: " + value);
            }
            dpi = OptionalInt.of(value);
        }

        int jobs =
                Math.max(
                        1,
                        CliOptionSupport.parseInt(
                                cmd,
                                DespeckleOptions.JOBS,
                                Runtime.getRuntime().availableProcessors()));
        boolean force = cmd.hasOption(DespeckleOptions.FORCE);

        return new Jbig2PackService.Config(imageDir, outPdf, source, dpi, jobs, force);
    }

    private int usageError(ParseException cause) {
        return CliOptionSupport.usageError(
                "despeckle topdf", SYNTAX, "despeckle topdf --help", cause);
    }
}
