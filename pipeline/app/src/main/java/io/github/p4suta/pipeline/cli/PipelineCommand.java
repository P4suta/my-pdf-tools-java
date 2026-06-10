package io.github.p4suta.pipeline.cli;

import io.github.p4suta.pipeline.application.PipelineRunner;
import io.github.p4suta.pipeline.infrastructure.DespeckleStage;
import io.github.p4suta.pipeline.infrastructure.G4EncodeStage;
import io.github.p4suta.pipeline.infrastructure.PdfExtractSource;
import io.github.p4suta.pipeline.infrastructure.RegisterStage;
import io.github.p4suta.pipeline.infrastructure.SourceMetadata;
import io.github.p4suta.pipeline.infrastructure.SpreadPackSink;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.pipeline.port.Source;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.shared.cli.BatchDriver;
import io.github.p4suta.shared.cli.CliDocs;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliLogging;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.cli.CliVersion;
import io.github.p4suta.shared.cli.InputResolver;
import io.github.p4suta.shared.cli.OutputGuard;
import io.github.p4suta.shared.cli.Prompt;
import io.github.p4suta.shared.observability.ExitCodes;
import io.github.p4suta.shared.progress.ProgressSink;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Command-line front end for the unified pipeline ({@code pdfbook}): one self-contained pass that
 * extracts a scan PDF once, despeckles and registers its pages over a shared image working-set, and
 * composes the RTL spread as the only repack — no intermediate PDFs. The composition root that
 * wires the Source/Stage/Sink adapters into {@link PipelineRunner}. The one place allowed to write
 * to {@code System.out}/{@code System.err}.
 *
 * <p>A single input PDF writes to the {@code -o} file; a directory (or several PDFs) batches every
 * top-level {@code *.pdf} into the {@code -o} directory, continue-on-error.
 */
public final class PipelineCommand {

    private static final String SYNTAX =
            "pdfbook [options] <in.pdf>... | <in-dir> -o <out.pdf|out-dir>";

    private static final String HEADER =
            "Convert self-scanned Japanese-book PDFs end-to-end in one pass: extract the scan's"
                + " pages once, remove scanner dust (despeckle), straighten and align them onto a"
                + " fixed canvas (register), then combine into right-to-left two-page spreads —"
                + " with no intermediate PDFs. One input writes to the -o file; a directory or"
                + " several PDFs batch every *.pdf into the -o directory (one failed book never"
                + " stops the rest).";

    private static final Options OPTIONS = buildOptions();

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(
                Option.builder("h").longOpt("help").desc("Show this help and exit.").get());
        options.addOption(
                Option.builder("V")
                        .longOpt("version")
                        .desc("Print version information and exit.")
                        .get());
        options.addOption(
                Option.builder("v")
                        .longOpt("verbose")
                        .desc("Enable verbose (DEBUG) logging.")
                        .get());
        options.addOption(
                Option.builder("i")
                        .longOpt("interactive")
                        .desc("Guided interactive mode: prompt for the input, options and output.")
                        .get());
        options.addOption(
                Option.builder("o")
                        .longOpt("output")
                        .hasArg()
                        .argName("path")
                        .desc("Output PDF (single input) or output directory (batch). Required.")
                        .get());
        options.addOption(
                Option.builder("d")
                        .longOpt("direction")
                        .hasArg()
                        .argName("RTL|LTR")
                        .desc("Reading direction of the spreads (default: RTL).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("first-page")
                        .hasArg()
                        .argName("right|left|cover")
                        .desc(
                                "Which side page one opens on: right (default), left (leading"
                                        + " blank), or cover (page one alone).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-despeckle")
                        .desc("Skip the dust-removal stage.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-register")
                        .desc("Skip the deskew/alignment stage.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-deskew")
                        .desc("In the register stage, do not straighten each page.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-scale")
                        .desc(
                                "In the register stage, do not scale columns to the reference"
                                        + " height.")
                        .get());
        options.addOption(
                Option.builder("j")
                        .longOpt("jobs")
                        .hasArg()
                        .argName("n")
                        .desc("Worker threads per book (default: available processors).")
                        .get());
        options.addOption(
                Option.builder().longOpt("pdf-a").desc("Emit PDF/A-2b conformance.").get());
        options.addOption(
                Option.builder()
                        .longOpt("force")
                        .desc(
                                "Overwrite an existing output PDF; in batch, regenerate outputs"
                                        + " that already exist instead of skipping them.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("progress-file")
                        .hasArg()
                        .argName("path")
                        .desc(
                                "Write machine-readable JSONL progress events to this file (single"
                                        + " input only); used by front ends to report progress.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("timings")
                        .desc(
                                "Print a per-stage wall-clock breakdown to stderr when each run"
                                        + " ends.")
                        .get());
        CliDocs.options(options);
        return options;
    }

    /**
     * Parses {@code args}, runs the pipeline, and returns the process exit code (0 success, 2
     * usage, else a sysexits code from the shared mapper).
     *
     * @param args the command-line arguments
     * @return the exit code
     */
    public int run(String[] args) {
        // A bare invocation prints help and succeeds, so newcomers see usage rather than an error.
        if (args.length == 0) {
            CliOptionSupport.printHelp("pdfbook", SYNTAX, HEADER, OPTIONS);
            return ExitCodes.OK;
        }
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            return usageError(e);
        }
        boolean verbose = cmd.hasOption("verbose");
        if (verbose) {
            CliLogging.enableDebug();
        }
        if (cmd.hasOption("help")) {
            CliOptionSupport.printHelp("pdfbook", SYNTAX, HEADER, OPTIONS);
            return ExitCodes.OK;
        }
        if (cmd.hasOption("version")) {
            System.out.println(CliVersion.line("pdfbook", PipelineCommand.class));
            return ExitCodes.OK;
        }
        int docs =
                CliDocs.handle(
                        cmd, "pdfbook", PipelineCommand.class, SYNTAX, HEADER, OPTIONS, List.of());
        if (docs >= 0) {
            return docs;
        }
        if (cmd.hasOption("interactive")) {
            return runInteractive(verbose);
        }
        try {
            return dispatch(cmd);
        } catch (ParseException e) {
            return usageError(e);
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> verbose).handle(e);
        }
    }

    private int dispatch(CommandLine cmd) throws IOException, ParseException {
        if (!cmd.hasOption("output")) {
            throw new ParseException("missing required -o/--output");
        }
        Path output = Path.of(cmd.getOptionValue("output"));

        List<String> rawInputs = cmd.getArgList();
        if (rawInputs.isEmpty()) {
            throw new ParseException("no input PDF given");
        }
        InputResolver.Resolved resolved =
                InputResolver.resolve(rawInputs, InputResolver.globFilter("*.pdf"));
        if (resolved.stdin()) {
            throw new ParseException("stdin input is not supported; pass a PDF path or directory");
        }
        List<Path> inputs = resolved.files();
        if (inputs.isEmpty()) {
            throw new ParseException("no *.pdf inputs found");
        }

        Config config = parseConfig(cmd);

        if (inputs.size() == 1) {
            OutputGuard.refuseIfExists(output, config.force());
            Path progressFile =
                    cmd.hasOption("progress-file")
                            ? Path.of(cmd.getOptionValue("progress-file"))
                            : null;
            runOne(inputs.get(0), output, config, progressFile);
            return ExitCodes.OK;
        }

        // Batch: -o is a directory; each book is written to <output>/<same-name>,
        // continue-on-error. --progress-file is single-input only, so it is ignored here.
        Files.createDirectories(output);
        // Skip a book whose output already exists unless --force, mirroring the register/despeckle
        // batch convention (single inputs refuse; batch keeps going past what is already done).
        List<Path> pending = new ArrayList<>();
        for (Path in : inputs) {
            if (!config.force() && Files.exists(output.resolve(outputName(in)))) {
                System.err.println(in.getFileName() + ": skipped (exists; use --force)");
            } else {
                pending.add(in);
            }
        }
        return new BatchDriver<Path>()
                .run(
                        pending,
                        (in, index, total) ->
                                String.format(
                                        Locale.ROOT, "[%d/%d] %s", index, total, in.getFileName()),
                        in -> runOne(in, output.resolve(outputName(in)), config, null));
    }

    /**
     * Guided interactive flow: prompt for the input, options and output, then run a single
     * conversion with a live console progress bar. Requires a terminal; a piped / non-TTY {@code
     * -i} fails fast with a usage error rather than blocking on a read.
     */
    private int runInteractive(boolean verbose) {
        Prompt prompt;
        try {
            prompt = Prompt.console();
        } catch (IllegalStateException e) {
            System.err.println("pdfbook: " + e.getMessage());
            return ExitCodes.USAGE;
        }
        try {
            @Nullable Plan plan = plan(prompt);
            if (plan == null) {
                prompt.say("Cancelled.");
                return ExitCodes.OK;
            }
            runWith(plan.input(), plan.output(), plan.config(), ConsoleProgressSink.forTerminal());
            return ExitCodes.OK;
        } catch (UncheckedIOException e) {
            @Nullable Throwable cause = e.getCause();
            System.err.println("pdfbook: " + (cause != null ? cause.getMessage() : e.getMessage()));
            return ExitCodes.USAGE;
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> verbose).handle(e);
        }
    }

    /** A confirmed interactive run: the input, output and assembled config. */
    record Plan(Path input, Path output, Config config) {}

    /**
     * Drives the guided questions over {@code prompt} and returns the {@link Plan}, or {@code null}
     * if the user declines. Pure prompt/filesystem interaction (no pipeline run, no console
     * coupling), so it is unit-tested with a scripted {@link Prompt}.
     *
     * @param prompt the interactive prompt (injected)
     * @return the plan, or {@code null} when cancelled
     */
    static @Nullable Plan plan(Prompt prompt) {
        prompt.say("pdfbook — interactive setup");

        Path input = askInputPdf(prompt);
        ReadingDirection direction =
                prompt.select(
                        "Reading direction",
                        List.of(
                                Prompt.Choice.of(
                                        "RTL (Japanese vertical, right-to-left)",
                                        ReadingDirection.RTL),
                                Prompt.Choice.of("LTR (left-to-right)", ReadingDirection.LTR)),
                        Prompt.Choice.of("RTL", ReadingDirection.RTL));
        FirstPageMode firstPage =
                prompt.select(
                        "First page opens on",
                        List.of(
                                Prompt.Choice.of("right (standard)", FirstPageMode.STANDARD),
                                Prompt.Choice.of(
                                        "left (leading blank)", FirstPageMode.LEADING_BLANK),
                                Prompt.Choice.of("cover (page one alone)", FirstPageMode.COVER)),
                        Prompt.Choice.of("right", FirstPageMode.STANDARD));
        boolean despeckle = prompt.confirm("Remove scanner dust (despeckle)?", true);
        boolean register = prompt.confirm("Straighten & align pages (register)?", true);
        boolean deskew = !register || prompt.confirm("  Straighten each page (deskew)?", true);
        boolean scale =
                !register || prompt.confirm("  Scale columns to the reference height?", true);
        boolean pdfA = prompt.confirm("Emit PDF/A-2b for archiving?", false);

        Path output = Path.of(prompt.ask("Output PDF", defaultOutput(input)));
        boolean force = false;
        if (Files.exists(output)) {
            if (!prompt.confirm(output + " exists. Overwrite?", false)) {
                return null;
            }
            force = true;
        }
        if (!prompt.confirm("Start conversion?", true)) {
            return null;
        }
        Config config =
                new Config(
                        Math.max(1, Runtime.getRuntime().availableProcessors()),
                        direction,
                        firstPage,
                        despeckle,
                        register,
                        deskew,
                        scale,
                        pdfA,
                        false,
                        force);
        return new Plan(input, output, config);
    }

    private static Path askInputPdf(Prompt prompt) {
        while (true) {
            Path input = Path.of(prompt.ask("Input PDF", null));
            if (Files.isRegularFile(input)) {
                return input;
            }
            prompt.say("  not a file: " + input);
        }
    }

    private static String defaultOutput(Path input) {
        @Nullable Path fileName = input.getFileName();
        String name = fileName == null ? "book" : fileName.toString();
        String base =
                name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                        ? name.substring(0, name.length() - 4)
                        : name;
        Path parent = input.toAbsolutePath().getParent();
        Path out = (parent == null ? Path.of(".") : parent).resolve(base + "_book.pdf");
        return out.toString();
    }

    private static void runOne(Path input, Path output, Config config, @Nullable Path progressFile)
            throws IOException {
        if (progressFile == null) {
            runWith(input, output, config, withTimings(config, ProgressSink.NO_OP));
        } else {
            try (JsonlFileProgressSink progress = new JsonlFileProgressSink(progressFile)) {
                runWith(input, output, config, withTimings(config, progress));
            }
        }
    }

    /**
     * Wraps {@code sink} with a fresh {@link StageTimingSink} when {@code --timings} is set, so
     * each run (every book of a batch separately) prints its own per-stage breakdown to stderr.
     */
    private static ProgressSink withTimings(Config config, ProgressSink sink) {
        if (!config.timings()) {
            return sink;
        }
        StageTimingSink timings = new StageTimingSink(System.err);
        if (sink == ProgressSink.NO_OP) {
            return timings;
        }
        return event -> {
            sink.emit(event);
            timings.emit(event);
        };
    }

    // Resolves the progress sink first so the stages and sink report page-level PageProcessed
    // events into the same sink PipelineRunner reports stage boundaries into. With no
    // --progress-file the sink is NO_OP and every emit is a no-op.
    private static void runWith(Path input, Path output, Config config, ProgressSink progress)
            throws IOException {
        List<Stage> stages = new ArrayList<>();
        if (config.despeckle()) {
            stages.add(new DespeckleStage(config.jobs(), progress));
        }
        if (config.register()) {
            stages.add(new RegisterStage(config.jobs(), config.deskew(), config.scale(), progress));
        }
        if (stages.isEmpty()) {
            // --no-despeckle --no-register: the raw pdfimages TIFFs are not CCITT G4, which the
            // spread sink's pass-through embedding requires; despeckle/register each re-encode G4
            // themselves, so only the no-stage path needs this normalization.
            stages.add(new G4EncodeStage(config.jobs(), progress));
        }
        Source source = new PdfExtractSource(input, config.jobs());
        // Carry the source book's title/author/etc. onto the output, matching the standalone tate
        // CLI (best-effort; empty if the source has none or cannot be read).
        DocumentMetadata metadata = SourceMetadata.read(input);
        Sink sink =
                new SpreadPackSink(
                        config.direction(),
                        config.firstPage(),
                        config.pdfA(),
                        MemoryMode.IN_MEMORY,
                        metadata,
                        progress);
        new PipelineRunner().run(source, stages, sink, output, progress);
    }

    private static String outputName(Path input) {
        Path name = input.getFileName();
        return name == null ? "out.pdf" : name.toString();
    }

    private static Config parseConfig(CommandLine cmd) throws ParseException {
        int jobs =
                Math.max(
                        1,
                        CliOptionSupport.parseInt(
                                cmd, "jobs", Runtime.getRuntime().availableProcessors()));
        ReadingDirection direction =
                CliOptionSupport.parseEnum(
                        ReadingDirection.class,
                        cmd.getOptionValue("direction"),
                        ReadingDirection.RTL);
        FirstPageMode firstPage = firstPageMode(cmd.getOptionValue("first-page", "right"));
        return new Config(
                jobs,
                direction,
                firstPage,
                !cmd.hasOption("no-despeckle"),
                !cmd.hasOption("no-register"),
                !cmd.hasOption("no-deskew"),
                !cmd.hasOption("no-scale"),
                cmd.hasOption("pdf-a"),
                cmd.hasOption("timings"),
                cmd.hasOption("force"));
    }

    private static FirstPageMode firstPageMode(String value) throws ParseException {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "right" -> FirstPageMode.STANDARD;
            case "left" -> FirstPageMode.LEADING_BLANK;
            case "cover" -> FirstPageMode.COVER;
            default ->
                    throw new ParseException(
                            "invalid --first-page '" + value + "' (right, left, or cover)");
        };
    }

    private int usageError(Exception cause) {
        return CliOptionSupport.usageError("pdfbook", SYNTAX, "pdfbook --help", cause);
    }

    /** Parsed, type-converted command line shared by single and batch runs. */
    record Config(
            int jobs,
            ReadingDirection direction,
            FirstPageMode firstPage,
            boolean despeckle,
            boolean register,
            boolean deskew,
            boolean scale,
            boolean pdfA,
            boolean timings,
            boolean force) {}
}
