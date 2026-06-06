package io.github.p4suta.despeckle.cli;

import io.github.p4suta.despeckle.application.DespeckleService;
import io.github.p4suta.despeckle.domain.model.OutputFormat;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.infrastructure.leptonica.LeptonicaPageCleaner;
import io.github.p4suta.despeckle.infrastructure.report.HtmlReporterFactory;
import io.github.p4suta.shared.cli.CliDocs;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliLogging;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.cli.CliVersion;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Command-line front end: parse arguments, build a {@link DespeckleService.Config}, run.
 *
 * <p>{@link #run(String[])} returns a picocli-compatible exit code (0 success, 2 usage/parse error,
 * 1 runtime error) rather than calling {@code System.exit}, so {@code Main} owns the process exit
 * and the parser can be exercised directly in tests. This is also the one class allowed to write to
 * the standard streams (help/version/usage), which the {@code noStandardStreams} architecture rule
 * carves out by name.
 */
public final class DespeckleCli {

    private static final String SYNTAX = "despeckle <INPUT_DIR> <OUTPUT_DIR> [options]";
    private static final String DESCRIPTION =
            "Remove scanner dust from bitonal Japanese-novel scans. To clean PDFs end-to-end"
                + " (pdfimages -> despeckle -> lossless JBIG2), use 'despeckle pipeline <in.pdf>"
                + " <out.pdf>'; a directory there batches every top-level *.pdf. 'despeckle topdf"
                + " <image-dir> <out.pdf>' packs already-cleaned pages into a JBIG2 PDF.";

    private final Options options = DespeckleOptions.build();

    /** Parse {@code args}, run the pipeline, and return the process exit code. */
    public int run(String[] args) {
        if (args.length > 0 && "pipeline".equals(args[0])) {
            return new PipelineCli().run(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length > 0 && "topdf".equals(args[0])) {
            return new TopdfCli().run(Arrays.copyOfRange(args, 1, args.length));
        }
        // A bare invocation prints help and succeeds, so newcomers see usage rather than an error.
        if (args.length == 0) {
            CliOptionSupport.printHelp("despeckle", SYNTAX, DESCRIPTION, options);
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
            CliOptionSupport.printHelp("despeckle", SYNTAX, DESCRIPTION, options);
            return ExitCodes.OK;
        }
        if (cmd.hasOption(DespeckleOptions.VERSION)) {
            System.out.println(CliVersion.line("despeckle", DespeckleCli.class));
            return ExitCodes.OK;
        }
        int docs =
                CliDocs.handle(
                        cmd,
                        "despeckle",
                        DespeckleCli.class,
                        SYNTAX,
                        DESCRIPTION,
                        options,
                        List.of("pipeline", "topdf"));
        if (docs >= 0) {
            return docs;
        }

        Parsed parsed;
        try {
            parsed = parseArgs(cmd);
        } catch (ParseException e) {
            return usageError(e);
        }

        // The split is deliberate: type/usage problems above are the usage exit 2, while value
        // validation (ProcessOptions) and pipeline I/O below are mapped to their sysexits codes by
        // the shared ExceptionMapper (bad value -> 64, image unreadable -> 65, not found -> 66,
        // output conflict -> 73, native-tool failure -> 70, OOM -> 137).
        try {
            new DespeckleService(new LeptonicaPageCleaner(), new HtmlReporterFactory())
                    .run(toConfig(parsed));
            return ExitCodes.OK;
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> verbose).handle(e);
        }
    }

    /** Type-converts and validates the parsed command line; failures here are usage errors. */
    Parsed parseArgs(CommandLine cmd) throws ParseException {
        List<String> positionals =
                CliOptionSupport.requireNPositionals(cmd, 2, "<INPUT_DIR>", "<OUTPUT_DIR>");
        Path inputDir = Path.of(positionals.get(0));
        Path outputDir = Path.of(positionals.get(1));

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

        OutputFormat format =
                CliOptionSupport.parseEnum(
                        OutputFormat.class,
                        cmd.getOptionValue(DespeckleOptions.FORMAT),
                        OutputFormat.SAME);

        String glob = cmd.getOptionValue(DespeckleOptions.GLOB, DespeckleOptions.DEFAULT_GLOB);

        boolean force = cmd.hasOption(DespeckleOptions.FORCE);

        OptionalInt dpi = DespeckleOptions.optionalInt(cmd, DespeckleOptions.DPI);
        OptionalInt speckSize = DespeckleOptions.optionalInt(cmd, DespeckleOptions.SPECK_SIZE);
        OptionalInt isolatedDustSize =
                DespeckleOptions.optionalInt(cmd, DespeckleOptions.ISOLATED_DUST_SIZE);

        // Hole-filling and the isolated-dust pass are on by default; an --x flag opts in,
        // a --no-x flag opts out (optIn || !optOut).
        boolean fillHoles =
                cmd.hasOption(DespeckleOptions.FILL_HOLES)
                        || !cmd.hasOption(DespeckleOptions.NO_FILL_HOLES);
        boolean removeIsolatedDust =
                cmd.hasOption(DespeckleOptions.REMOVE_ISOLATED_DUST)
                        || !cmd.hasOption(DespeckleOptions.NO_REMOVE_ISOLATED_DUST);

        return new Parsed(
                inputDir,
                outputDir,
                reportDir,
                flipbook,
                jobs,
                format,
                glob,
                force,
                dpi,
                speckSize,
                isolatedDustSize,
                fillHoles,
                removeIsolatedDust);
    }

    /** Assembles the service config; {@link ProcessOptions} rejects non-positive sizes here. */
    static DespeckleService.Config toConfig(Parsed parsed) {
        ProcessOptions processOptions =
                new ProcessOptions(
                        parsed.dpi(),
                        parsed.speckSize(),
                        parsed.fillHoles(),
                        parsed.removeIsolatedDust(),
                        parsed.isolatedDustSize());
        return new DespeckleService.Config(
                parsed.inputDir(),
                parsed.outputDir(),
                parsed.format(),
                parsed.glob(),
                Math.max(1, parsed.jobs()),
                parsed.force(),
                processOptions,
                parsed.reportDir(),
                parsed.flipbook());
    }

    private int usageError(ParseException cause) {
        return CliOptionSupport.usageError("despeckle", SYNTAX, "despeckle --help", cause);
    }

    /** Parsed, type-converted command-line values, before {@link ProcessOptions} validation. */
    record Parsed(
            Path inputDir,
            Path outputDir,
            @Nullable Path reportDir,
            boolean flipbook,
            int jobs,
            OutputFormat format,
            String glob,
            boolean force,
            OptionalInt dpi,
            OptionalInt speckSize,
            OptionalInt isolatedDustSize,
            boolean fillHoles,
            boolean removeIsolatedDust) {}
}
