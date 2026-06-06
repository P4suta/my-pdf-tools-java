package io.github.p4suta.shared.cli;

import io.github.p4suta.shared.observability.ExitCodes;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Wires the self-documenting flags every CLI shares: {@code --completion <shell>} (prints a
 * bash/zsh/fish completion script) and {@code --man} (prints a troff man page). Both are generated
 * from the command's own {@link Options} model, so they never drift from {@code --help}.
 *
 * <p>A front end adds {@link #options(Options)} to its model and, right after parsing, returns
 * {@link #handle} when it fires — the two flags short-circuit like {@code --help}/{@code
 * --version}.
 */
public final class CliDocs {

    /** The option name for the completion-script flag. */
    public static final String COMPLETION = "completion";

    /** The option name for the man-page flag. */
    public static final String MAN = "man";

    private CliDocs() {}

    /**
     * Adds {@code --completion <shell>} and {@code --man} to {@code options}.
     *
     * @param options the option model to extend
     */
    public static void options(Options options) {
        options.addOption(
                Option.builder()
                        .longOpt(COMPLETION)
                        .hasArg()
                        .argName("bash|zsh|fish")
                        .desc("Print a shell completion script and exit.")
                        .get());
        options.addOption(
                Option.builder().longOpt(MAN).desc("Print a man page (troff) and exit.").get());
    }

    /**
     * If {@code cmd} carries {@code --completion} or {@code --man}, prints the artifact to {@code
     * System.out} and returns its exit code; otherwise returns {@code -1} so the caller proceeds.
     *
     * @param cmd the parsed command line
     * @param program the command name (e.g. {@code pdfbook})
     * @param anchorClass a class in the app's package, for the version footer
     * @param syntax the usage syntax line
     * @param header the description paragraph shown by {@code --help}
     * @param options the command's option model
     * @param subcommands the subcommand tokens (e.g. {@code pipeline}), or empty
     * @return the exit code to return, or {@code -1} when neither flag is present
     */
    public static int handle(
            CommandLine cmd,
            String program,
            Class<?> anchorClass,
            String syntax,
            String header,
            Options options,
            List<String> subcommands) {
        if (cmd.hasOption(COMPLETION)) {
            ShellCompletion.Shell shell;
            try {
                shell = ShellCompletion.Shell.from(cmd.getOptionValue(COMPLETION));
            } catch (IllegalArgumentException e) {
                System.err.println(
                        program
                                + ": --completion expects bash, zsh or fish, but got: '"
                                + cmd.getOptionValue(COMPLETION)
                                + "'");
                return ExitCodes.USAGE;
            }
            System.out.println(ShellCompletion.render(shell, program, options, subcommands));
            return ExitCodes.OK;
        }
        if (cmd.hasOption(MAN)) {
            System.out.println(
                    ManPage.troff(program, CliVersion.value(anchorClass), syntax, header, options));
            return ExitCodes.OK;
        }
        return -1;
    }
}
