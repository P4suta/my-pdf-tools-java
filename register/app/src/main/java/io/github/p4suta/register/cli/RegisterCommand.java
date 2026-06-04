package io.github.p4suta.register.cli;

import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.diag.DiagnosticsReporterFactory;
import io.github.p4suta.register.infrastructure.registrar.LeptonicaPageRegistrar;
import io.github.p4suta.shared.cli.CliExceptionHandler;
import io.github.p4suta.shared.cli.CliOptionSupport;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * Command-line front end: parse arguments with Apache Commons CLI, build a {@link
 * RegistrationService.Config}, run. This is also the composition root that wires the Leptonica
 * registrar and diagnostics reporter into the service. The one place allowed to write to {@code
 * System.out}/{@code System.err} — help, version and parse errors print here, while everything
 * below the shell logs through SLF4J.
 */
public final class RegisterCommand {

    private static final String SYNTAX = "register [options] <inputDir> <outputDir>";

    // The help formatter reflows the header as one paragraph, so keep it as a single block: the
    // description plus what the two positionals mean (Commons CLI has no positional model to
    // render).
    private static final String HEADER =
            "Align bitonal Japanese-novel scans onto a fixed paper-size canvas, removing scan"
                + " jitter in page size, position and skew. Positional arguments: <inputDir> is the"
                + " directory of bitonal page images (read recursively); <outputDir> is where"
                + " registered images are written (mirrors the input layout).";

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
                Option.builder()
                        .longOpt("paper")
                        .hasArg()
                        .argName("size")
                        .desc(
                                "Target paper size: auto, a standard name (shiroku, a4, a5, a6, b5,"
                                    + " b6, shinsho) or a custom WxH in millimeters (e.g. 127x188)."
                                    + " Default: auto — snap to the nearest standard book size of"
                                    + " the median scanned page, falling back to its exact size.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("dpi")
                        .hasArg()
                        .argName("n")
                        .desc(
                                "Output resolution; fixes the canvas pixel size for the paper."
                                    + " Default: inherit the input scan's own resolution, falling"
                                    + " back to "
                                        + RegisterOptions.DEFAULT_DPI
                                        + " when the inputs carry none (e.g. raw PBM).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("format")
                        .hasArg()
                        .argName("fmt")
                        .desc("Output format: same, pbm, png or tiff (default: same).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("glob")
                        .hasArg()
                        .argName("pattern")
                        .desc("Glob for input file names (default: *.{pbm,png,tiff,tif}).")
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
                        .desc("Overwrite a non-empty output directory.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-deskew")
                        .desc(
                                "Do not straighten each page before detection (deskew is on by"
                                        + " default).")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("no-scale")
                        .desc(
                                "Do not scale each page's column to the reference height (scaling"
                                        + " is on by default).")
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
                        .desc(
                                "Where to register the column: center or top_right (default:"
                                    + " top_right). top_right pins the column's top-right corner to"
                                    + " the per-parity reference and crops the scan margin that"
                                    + " overflows, so the text block lands at the same canvas"
                                    + " position on every page; center balances each page's margins"
                                    + " on the canvas without cropping.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("diag")
                        .hasArg()
                        .argName("dir")
                        .desc(
                                "Development aid: write per-page diagnostic overlays (detected"
                                    + " column, band, reference, placement, skew + projection"
                                    + " profiles), a JSONL log, a summary, a corpus before/after"
                                    + " overlay (corpus-overlay.png) and a residual chart"
                                    + " (residuals.png) to this directory. Off by default.")
                        .get());
        options.addOption(
                Option.builder()
                        .longOpt("flipbook")
                        .desc(
                                "With --diag, also assemble the registered pages into an animated"
                                    + " WebP flip-book (flipbook.webp) so the steady text block is"
                                    + " visible; needs libwebp's img2webp on the PATH (or"
                                    + " -Dregister.img2webp.path). Off by default.")
                        .get());
        return options;
    }

    /**
     * Parse, then run, returning a process exit code: {@code 0} on success, {@code 2} on a usage
     * error (bad option or argument), and a sysexits-flavored code on a run failure (the shared
     * {@code ExceptionMapper} maps the failure's {@code ErrorCategory} to its {@code exitCode()} —
     * e.g. 65 image-unreadable, 66 input-not-found, 70 native-tool / internal, 73 output-conflict).
     * Help and version short-circuit to 0. The sole stdout/stderr writer.
     *
     * @param args the raw command-line arguments
     * @return the process exit code
     */
    public int execute(String[] args) {
        // `register pipeline <in.pdf> <out.pdf>` is the PDF -> PDF mode; the default (no
        // subcommand) is the image-directory mode below.
        if (args.length > 0 && "pipeline".equals(args[0])) {
            return new PipelineCommand().execute(Arrays.copyOfRange(args, 1, args.length));
        }
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
        if (cmd.hasOption("version")) {
            printOut(versionLine());
            return ExitCodes.OK;
        }
        RegistrationService.Config config;
        try {
            config = buildConfig(cmd);
        } catch (ParseException | IllegalArgumentException e) {
            return usageError(e);
        }
        try {
            registrationService().run(config);
            return ExitCodes.OK;
        } catch (IOException | RuntimeException e) {
            return new CliExceptionHandler(() -> false).handle(e);
        }
    }

    /**
     * The registration service wired to its infrastructure adapters: the Leptonica page registrar
     * and the diagnostics reporter factory. This {@code cli} package is the composition root that
     * assembles the application service from the {@code :infrastructure} adapters.
     */
    private static RegistrationService registrationService() {
        return new RegistrationService(
                new LeptonicaPageRegistrar(), new DiagnosticsReporterFactory());
    }

    /**
     * Parse {@code args} into a {@link RegistrationService.Config} without running. Package-private
     * so the CLI parsing can be unit-tested.
     *
     * @param args the raw command-line arguments
     * @return the parsed run configuration
     * @throws ParseException if the options or positionals are malformed
     */
    RegistrationService.Config parse(String[] args) throws ParseException {
        return buildConfig(new DefaultParser().parse(OPTIONS, args));
    }

    private static RegistrationService.Config buildConfig(CommandLine cmd) throws ParseException {
        List<String> positionals =
                CliOptionSupport.requireNPositionals(cmd, 2, "<inputDir>", "<outputDir>");
        Path inputDir = Path.of(positionals.get(0));
        Path outputDir = Path.of(positionals.get(1));

        RegisterOptions options = CliSupport.buildRegisterOptions(cmd);

        @Nullable String diag = cmd.getOptionValue("diag");
        boolean flipbook = cmd.hasOption("flipbook");
        if (flipbook && diag == null) {
            throw new ParseException("--flipbook needs --diag (it writes flipbook.webp there)");
        }
        return new RegistrationService.Config(
                inputDir,
                outputDir,
                CliOptionSupport.parseEnum(
                        OutputFormat.class, cmd.getOptionValue("format"), OutputFormat.SAME),
                cmd.getOptionValue("glob", "*.{pbm,png,tiff,tif}"),
                Math.max(
                        1,
                        CliOptionSupport.parseInt(
                                cmd, "jobs", Runtime.getRuntime().availableProcessors())),
                cmd.hasOption("force"),
                options,
                diag == null ? null : Path.of(diag),
                flipbook);
    }

    private int usageError(Exception cause) {
        return CliOptionSupport.usageError("register", SYNTAX, "register --help", cause);
    }

    private void printHelp() {
        CliOptionSupport.printHelp("register", SYNTAX, HEADER, OPTIONS);
    }

    private static String versionLine() {
        String version = RegisterCommand.class.getPackage().getImplementationVersion();
        return "register " + (version == null ? "(dev)" : version);
    }

    private void printOut(String line) {
        System.out.println(line);
    }
}
