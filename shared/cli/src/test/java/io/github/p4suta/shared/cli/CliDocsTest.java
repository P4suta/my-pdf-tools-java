package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.observability.ExitCodes;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

/** The self-documenting flags: completion-script and man-page generation from the Options model. */
final class CliDocsTest {

    private static Options sampleOptions() {
        Options options = new Options();
        options.addOption(
                Option.builder("o").longOpt("output").hasArg().argName("path").desc("Out.").get());
        options.addOption(Option.builder("h").longOpt("help").desc("Show help.").get());
        CliDocs.options(options);
        return options;
    }

    private static CommandLine parse(String... args) throws Exception {
        return new DefaultParser().parse(sampleOptions(), args);
    }

    @Test
    void absentFlagsReturnMinusOne() throws Exception {
        int code =
                CliDocs.handle(
                        parse("-o", "x"),
                        "tool",
                        CliDocsTest.class,
                        "tool [opts]",
                        "Desc.",
                        sampleOptions(),
                        List.of());
        assertThat(code).isEqualTo(-1);
    }

    @Test
    void completionFlagReturnsOk() throws Exception {
        for (String shell : List.of("bash", "zsh", "fish")) {
            int code =
                    CliDocs.handle(
                            parse("--completion", shell),
                            "tool",
                            CliDocsTest.class,
                            "tool [opts]",
                            "Desc.",
                            sampleOptions(),
                            List.of("sub"));
            assertThat(code).as(shell).isEqualTo(ExitCodes.OK);
        }
    }

    @Test
    void unknownShellIsUsageError() throws Exception {
        int code =
                CliDocs.handle(
                        parse("--completion", "powershell"),
                        "tool",
                        CliDocsTest.class,
                        "tool [opts]",
                        "Desc.",
                        sampleOptions(),
                        List.of());
        assertThat(code).isEqualTo(ExitCodes.USAGE);
    }

    @Test
    void manFlagReturnsOk() throws Exception {
        int code =
                CliDocs.handle(
                        parse("--man"),
                        "tool",
                        CliDocsTest.class,
                        "tool [opts]",
                        "Desc.",
                        sampleOptions(),
                        List.of());
        assertThat(code).isEqualTo(ExitCodes.OK);
    }

    @Test
    void bashScriptListsFlagsAndSubcommands() {
        String script =
                ShellCompletion.render(
                        ShellCompletion.Shell.BASH, "tool", sampleOptions(), List.of("sub"));
        assertThat(script)
                .contains("--output")
                .contains("-o")
                .contains("complete -F")
                .contains("sub");
    }

    @Test
    void fishScriptUsesCompleteLines() {
        String script =
                ShellCompletion.render(
                        ShellCompletion.Shell.FISH, "tool", sampleOptions(), List.of());
        assertThat(script).contains("complete -c tool").contains("-l output").contains("-r");
    }

    @Test
    void zshScriptIsCompdef() {
        String script =
                ShellCompletion.render(
                        ShellCompletion.Shell.ZSH, "tool", sampleOptions(), List.of("sub"));
        assertThat(script).contains("#compdef tool").contains("_arguments");
    }

    @Test
    void manPageHasStandardSections() {
        String man =
                ManPage.troff(
                        "tool", "1.2.3", "tool [opts]", "Does a thing. Always.", sampleOptions());
        assertThat(man)
                .contains(".TH TOOL 1")
                .contains("tool 1.2.3")
                .contains(".SH NAME")
                .contains(".SH SYNOPSIS")
                .contains(".SH OPTIONS")
                .contains("\\-\\-output");
    }
}
