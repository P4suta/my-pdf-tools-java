package io.github.p4suta.tateyokopdf.cli;

import io.github.p4suta.shared.cli.BatchDriver;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.InputResolver;
import io.github.p4suta.shared.cli.OutputTarget;
import io.github.p4suta.shared.cli.StdinSource;
import io.github.p4suta.tateyokopdf.application.SpreadOptions;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import io.github.p4suta.tateyokopdf.infrastructure.qpdf.QpdfLinearizer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Command-line front-end built on Apache Commons CLI.
 *
 * <p>Accepts one or more inputs (files, directories, or {@code -} for stdin), resolves them to
 * concrete PDFs via the shared {@link InputResolver}, and runs {@link FileConversion} for each.
 * Diagnostics and progress go to stderr; with {@code -o -} the converted PDF is streamed to stdout
 * as a clean binary stream. The stdin/stdout temp-file bridge ({@link StdinSource}, {@link
 * OutputTarget}), the continue-on-error batch loop ({@link BatchDriver}), and the throwable&rarr;
 * exit-code handler ({@link CliExceptionHandler}) are the shared {@code
 * io.github.p4suta.shared.cli} scaffolding; tate keeps only its own option model, help text, {@code
 * --version} string, argument interpretation ({@link CliArguments}), and this composition root.
 */
public final class SpreadCommand {

    static final String NAME = "tate-yoko-pdf";
    static final String VERSION = "tate-yoko-pdf 0.1.0";

    private SpreadCommand() {}

    // ---- entry points -------------------------------------------------------

    public static void main(String[] args) {
        runCli(args);
    }

    public static void runCli(String[] args) {
        System.exit(run(args));
    }

    /**
     * Parses {@code args}, runs the conversion(s), and returns the exit code (never calls exit).
     */
    public static int run(String[] args) {
        Options options = buildOptions();
        boolean verbose = false;
        try {
            CommandLine cmd = new DefaultParser().parse(options, args);
            verbose = cmd.hasOption("verbose");
            if (verbose) {
                configureVerboseLogging();
            }
            if (cmd.hasOption("help")) {
                printHelp(System.out);
                return CliExitCodes.OK;
            }
            if (cmd.hasOption("version")) {
                System.out.println(VERSION);
                return CliExitCodes.OK;
            }
            if (cmd.getArgList().isEmpty()) {
                printHelp(System.out);
                return CliExitCodes.OK;
            }
            return execute(CliArguments.from(cmd));
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            printHelp(System.err);
            return CliExitCodes.USAGE;
        } catch (Exception e) {
            boolean v = verbose;
            return new CliExceptionHandler(() -> v).handle(e);
        }
    }

    // ---- orchestration ------------------------------------------------------

    private static int execute(CliArguments args) throws IOException, ParseException {
        // Composition root: assemble the pipeline once, then dispatch per input.
        FileConversion conversion =
                new FileConversion(
                        new PdfBoxDocumentFactory(args.memoryMode()),
                        new SpreadLayoutCalculator(),
                        QpdfLinearizer.create(),
                        args);

        InputResolver.Resolved resolved = args.inputs();
        @Nullable String outputOpt = args.outputOpt();

        if (resolved.stdin()) {
            OutputTarget target =
                    (outputOpt == null || "-".equals(outputOpt))
                            ? OutputTarget.stdout("tate-yoko-out", ".pdf")
                            : OutputTarget.file(Path.of(outputOpt));
            StdinSource.withStdin(
                    "tate-yoko-in", ".pdf", in -> conversion.convert(in, target, null));
            return CliExitCodes.OK;
        }

        List<Path> files = resolved.files();
        if (files.isEmpty()) {
            throw new ParseException("no PDF files found in the given inputs");
        }

        // Single input: fail-fast — let the exception bubble up to run()'s mapper.
        if (files.size() == 1) {
            Path input = files.get(0);
            conversion.convert(input, singleOutput(input, outputOpt), null);
            return CliExitCodes.OK;
        }

        // Batch: continue-on-error via the shared BatchDriver. It aggregates failures and reports
        // one "Error[KIND] <path>: <message>" line per failure. The driver returns the first
        // failure's sysexits-flavored exit code, but tate's CLI contract is a flat GENERIC_ERROR
        // (1) on any batch failure, so we collapse the driver's non-zero aggregate back to 1 below.
        if ("-".equals(outputOpt)) {
            throw new ParseException("cannot write multiple inputs to stdout ('-o -')");
        }
        @Nullable Path outDir = batchOutputDir(outputOpt);
        int total = files.size();
        List<BatchItem> items = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Path input = files.get(i);
            // Precompute the "[i/n] filename" progress prefix here, since the BatchDriver processor
            // (unlike its labeler) gets only the item — bundling it keeps the
            // ConsoleProgressListener
            // prefix that batch runs have always shown.
            String progress = "[" + (i + 1) + "/" + total + "] " + input.getFileName();
            items.add(new BatchItem(input, batchOutput(input, outDir), progress));
        }
        // The labeler renders the raw path for the per-failure error line ("Error[KIND] <path>:
        // <message>"), matching tate's prior batch reporting; the processor threads the precomputed
        // progress prefix into the ConsoleProgressListener.
        int code =
                new BatchDriver<BatchItem>()
                        .run(
                                items,
                                (item, index, count) -> item.input().toString(),
                                item ->
                                        conversion.convert(
                                                item.input(), item.target(), item.progressLabel()));
        // Map any failure (the driver's sysexits aggregate) back to tate's flat GENERIC_ERROR (1).
        return code == CliExitCodes.OK ? CliExitCodes.OK : CliExitCodes.GENERIC_ERROR;
    }

    /**
     * One unit of batch work: a source PDF, the resolved destination for its conversion, and the
     * precomputed {@code [i/n] filename} progress prefix shown on its progress lines.
     */
    private record BatchItem(Path input, OutputTarget target, String progressLabel) {}

    // ---- output resolution --------------------------------------------------

    private static OutputTarget singleOutput(Path input, @Nullable String outputOpt) {
        if (outputOpt == null) {
            return OutputTarget.file(SpreadOptions.withDefaults(input).outputPath());
        }
        if ("-".equals(outputOpt)) {
            return OutputTarget.stdout("tate-yoko-out", ".pdf");
        }
        return OutputTarget.file(Path.of(outputOpt));
    }

    private static @Nullable Path batchOutputDir(@Nullable String outputOpt)
            throws IOException, ParseException {
        if (outputOpt == null) {
            return null; // write each output next to its input
        }
        Path dir = Path.of(outputOpt);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new ParseException(
                    "-o must be a directory when multiple inputs are given: " + dir);
        }
        Files.createDirectories(dir);
        return dir;
    }

    private static OutputTarget batchOutput(Path input, @Nullable Path outDir) {
        Path sibling = SpreadOptions.withDefaults(input).outputPath();
        if (outDir == null) {
            return OutputTarget.file(sibling);
        }
        Path name = Objects.requireNonNull(sibling.getFileName());
        return OutputTarget.file(outDir.resolve(name));
    }

    // ---- options & help -----------------------------------------------------

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(
                Option.builder("o")
                        .longOpt("output")
                        .hasArg()
                        .argName("FILE|DIR|-")
                        .desc(
                                "Output path; a directory for batch; '-' for stdout (default:"
                                        + " <input>_spread.pdf)")
                        .get());
        options.addOption(
                Option.builder("d")
                        .longOpt("direction")
                        .hasArg()
                        .argName("RTL|LTR")
                        .desc("Reading direction: RTL (default) or LTR")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("first-page")
                        .hasArg()
                        .argName("right|left|cover")
                        .desc(
                                "Which side page 1 opens on: right, left, or a standalone cover"
                                        + " (default: the reading direction's natural side)")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("pdf-a")
                        .desc(
                                "Emit PDF/A-2b for archiving (best-effort: adds the conformance"
                                        + " structure; full validity depends on the source PDF's"
                                        + " content)")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("low-memory")
                        .desc(
                                "Spill page streams to a temp file instead of the heap; bounds"
                                        + " memory for very large scans on memory-constrained hosts"
                                        + " (slightly slower; uses java.io.tmpdir)")
                        .get());
        options.addOption(
                Option.builder("v")
                        .longOpt("verbose")
                        .desc("Enable verbose logging output (DEBUG level)")
                        .get());
        options.addOption(
                Option.builder("h").longOpt("help").desc("Show this help and exit").get());
        options.addOption(Option.builder().longOpt("version").desc("Print version and exit").get());
        return options;
    }

    private static void printHelp(PrintStream out) {
        out.print(
                """
                Usage: tate-yoko-pdf [options] INPUT...

                Convert scanned PDF pages into a side-by-side spread layout for Japanese
                vertical text. INPUT may be one or more PDF files, a directory (its *.pdf
                children), or '-' to read a single PDF from stdin.

                Options:
                  -o, --output <FILE|DIR|->   Output path; a directory for batch; '-' for stdout
                                              (default: <input>_spread.pdf)
                  -d, --direction <RTL|LTR>   Reading direction (default: RTL)
                      --first-page <right|left|cover>
                                              Side page 1 opens on (default: direction's natural side);
                                              'left' (RTL) leads with a blank, 'cover' stands page 1 alone
                      --pdf-a                 Emit PDF/A-2b for archiving (best-effort; see docs)
                      --low-memory            Spill page streams to a temp file to bound heap on huge scans
                  -v, --verbose               Enable verbose (DEBUG) logging
                  -h, --help                  Show this help and exit
                      --version               Print version and exit

                Examples:
                  tate-yoko-pdf novel.pdf                       -> novel_spread.pdf (RTL)
                  tate-yoko-pdf novel.pdf --first-page left     page 1 on the left (leading blank)
                  tate-yoko-pdf scans/ -o out/                  batch a directory
                  cat in.pdf | tate-yoko-pdf - -o - > out.pdf   stdin -> stdout
                """);
    }

    private static void configureVerboseLogging() {
        // slf4j-simple is the binding (matching register/despeckle). Unlike Logback it has no
        // mutable LoggerContext: its per-logger threshold is read live from the
        // `org.slf4j.simpleLogger.log.<name>` system property the first time each logger is
        // constructed (SimpleLogger's constructor calls recursivelyComputeLevelString(), which
        // reads System.getProperty for that key, walking up the dotted name). The `defaultLogLevel`
        // property is cached at first-init and would be a dead write here, because
        // FatalUncaughtHandler's static logger already initialized slf4j-simple in Main before this
        // runs. So we scope DEBUG to the app's own + shared loggers via the live per-logger key;
        // every io.github.p4suta.* logger is built later (during execute()), after this set. This
        // is a deliberate, minor delta from the old root-DEBUG behavior: third-party loggers
        // (PDFBox etc.) stay at their default level instead of going DEBUG too.
        System.setProperty("org.slf4j.simpleLogger.log.io.github.p4suta", "debug");
    }
}
