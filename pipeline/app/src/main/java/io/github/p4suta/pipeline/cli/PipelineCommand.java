package io.github.p4suta.pipeline.cli;

import io.github.p4suta.pipeline.application.PipelineRunner;
import io.github.p4suta.pipeline.infrastructure.DespeckleStage;
import io.github.p4suta.pipeline.infrastructure.PdfExtractSource;
import io.github.p4suta.pipeline.infrastructure.RegisterStage;
import io.github.p4suta.pipeline.infrastructure.SpreadPackSink;
import io.github.p4suta.pipeline.port.Sink;
import io.github.p4suta.pipeline.port.Source;
import io.github.p4suta.pipeline.port.Stage;
import io.github.p4suta.shared.cli.BatchDriver;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.cli.InputResolver;
import io.github.p4suta.shared.observability.ExitCodes;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.io.IOException;
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
                        .longOpt("progress-file")
                        .hasArg()
                        .argName("path")
                        .desc(
                                "Write machine-readable JSONL progress events to this file (single"
                                        + " input only); used by front ends to report progress.")
                        .get());
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
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            return usageError(e);
        }
        if (cmd.hasOption("help")) {
            CliOptionSupport.printHelp("pdfbook", SYNTAX, HEADER, OPTIONS);
            return ExitCodes.OK;
        }
        try {
            return dispatch(cmd);
        } catch (ParseException e) {
            return usageError(e);
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> false).handle(e);
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
        return new BatchDriver<Path>()
                .run(
                        inputs,
                        (in, index, total) ->
                                String.format(
                                        Locale.ROOT, "[%d/%d] %s", index, total, in.getFileName()),
                        in -> runOne(in, output.resolve(outputName(in)), config, null));
    }

    private static void runOne(Path input, Path output, Config config, @Nullable Path progressFile)
            throws IOException {
        List<Stage> stages = new ArrayList<>();
        if (config.despeckle()) {
            stages.add(new DespeckleStage(config.jobs()));
        }
        if (config.register()) {
            stages.add(new RegisterStage(config.jobs(), config.deskew(), config.scale()));
        }
        Source source = new PdfExtractSource(input, config.jobs());
        Sink sink =
                new SpreadPackSink(
                        config.direction(),
                        config.firstPage(),
                        config.pdfA(),
                        MemoryMode.IN_MEMORY,
                        DocumentMetadata.empty());
        if (progressFile == null) {
            new PipelineRunner().run(source, stages, sink, output);
        } else {
            try (JsonlFileProgressSink progress = new JsonlFileProgressSink(progressFile)) {
                new PipelineRunner().run(source, stages, sink, output, progress);
            }
        }
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
                cmd.hasOption("pdf-a"));
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
    private record Config(
            int jobs,
            ReadingDirection direction,
            FirstPageMode firstPage,
            boolean despeckle,
            boolean register,
            boolean deskew,
            boolean scale,
            boolean pdfA) {}
}
