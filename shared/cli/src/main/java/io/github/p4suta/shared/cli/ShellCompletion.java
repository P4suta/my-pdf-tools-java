package io.github.p4suta.shared.cli;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jspecify.annotations.Nullable;

/**
 * Generates a shell completion script (bash / zsh / fish) for a Commons CLI command from its {@link
 * Options} model plus its subcommand tokens. Commons CLI has no completion or candidate metadata,
 * so the scripts complete option flags (long and short) and subcommand names and otherwise fall
 * back to file completion — option-argument values are not enumerated.
 */
public final class ShellCompletion {

    /** The shells a completion script can be rendered for. */
    public enum Shell {
        BASH,
        ZSH,
        FISH;

        /**
         * {@return the shell named {@code value} (case-insensitive)}
         *
         * @param value {@code bash}, {@code zsh} or {@code fish}
         * @throws IllegalArgumentException if {@code value} is not a known shell
         */
        public static Shell from(String value) {
            return Shell.valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }

    private ShellCompletion() {}

    /**
     * {@return a completion script for {@code shell}}
     *
     * @param shell the target shell
     * @param program the command name (e.g. {@code pdfbook})
     * @param options the command's option model
     * @param subcommands the subcommand tokens (e.g. {@code pipeline}, {@code topdf}), or empty
     */
    public static String render(
            Shell shell, String program, Options options, List<String> subcommands) {
        return switch (shell) {
            case BASH -> bash(program, options, subcommands);
            case ZSH -> zsh(program, options, subcommands);
            case FISH -> fish(program, options, subcommands);
        };
    }

    // Built with plain concatenation, not String.format: a completion script must carry literal
    // '\n', and a format string with embedded newlines is exactly what
    // VA_FORMAT_STRING_USES_NEWLINE
    // flags.
    private static String bash(String program, Options options, List<String> subcommands) {
        String flags =
                options.getOptions().stream()
                        .flatMap(o -> flagTokens(o).stream())
                        .collect(Collectors.joining(" "));
        String subs = String.join(" ", subcommands);
        String fn = "_" + program.replace('-', '_');
        return "# bash completion for "
                + program
                + " — source this file or drop it in a bash-completion.d directory.\n"
                + fn
                + "() {\n"
                + "  local cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
                + "  local opts=\""
                + flags
                + "\"\n"
                + "  local subs=\""
                + subs
                + "\"\n"
                + "  if [[ \"$cur\" == -* ]]; then\n"
                + "    COMPREPLY=( $(compgen -W \"$opts\" -- \"$cur\") )\n"
                + "    return\n"
                + "  fi\n"
                + "  COMPREPLY=( $(compgen -W \"$subs\" -f -- \"$cur\") )\n"
                + "}\n"
                + "complete -F "
                + fn
                + " "
                + program
                + "\n";
    }

    private static String zsh(String program, Options options, List<String> subcommands) {
        String fn = "_" + program.replace('-', '_');
        StringBuilder out = new StringBuilder();
        out.append("#compdef ").append(program).append('\n');
        out.append("# zsh completion for ").append(program).append('\n');
        out.append(fn).append("() {\n");
        out.append("  _arguments -s \\\n");
        for (Option o : options.getOptions()) {
            out.append(zshOptionLine(o)).append('\n');
        }
        if (!subcommands.isEmpty()) {
            out.append("    '1: :(").append(String.join(" ", subcommands)).append(")' \\\n");
        }
        out.append("    '*:file:_files'\n");
        out.append("}\n");
        out.append(fn).append(" \"$@\"\n");
        return out.toString();
    }

    private static String zshOptionLine(Option o) {
        String desc = sanitize(o.getDescription());
        String value = o.hasArg() ? ":value:" : "";
        String name = longFlag(o);
        if (name == null) {
            name = shortFlag(o);
        }
        return "    '" + name + "[" + desc + "]" + value + "' \\";
    }

    private static String fish(String program, Options options, List<String> subcommands) {
        StringBuilder out =
                new StringBuilder("# fish completion for ").append(program).append('\n');
        for (Option o : options.getOptions()) {
            out.append("complete -c ").append(program);
            if (o.getOpt() != null) {
                out.append(" -s ").append(o.getOpt());
            }
            if (o.getLongOpt() != null) {
                out.append(" -l ").append(o.getLongOpt());
            }
            if (o.hasArg()) {
                out.append(" -r");
            }
            out.append(" -d '").append(sanitize(o.getDescription())).append("'\n");
        }
        for (String sub : subcommands) {
            out.append("complete -c ")
                    .append(program)
                    .append(" -n __fish_use_subcommand -a ")
                    .append(sub)
                    .append(" -d 'subcommand'\n");
        }
        return out.toString();
    }

    private static List<String> flagTokens(Option o) {
        @Nullable String shortFlag = shortFlag(o);
        @Nullable String longFlag = longFlag(o);
        if (shortFlag != null && longFlag != null) {
            return List.of(shortFlag, longFlag);
        }
        if (longFlag != null) {
            return List.of(longFlag);
        }
        return shortFlag != null ? List.of(shortFlag) : List.of();
    }

    private static @Nullable String shortFlag(Option o) {
        return o.getOpt() == null ? null : "-" + o.getOpt();
    }

    private static @Nullable String longFlag(Option o) {
        return o.getLongOpt() == null ? null : "--" + o.getLongOpt();
    }

    /**
     * First line only, single quotes/brackets/colons neutralized so the script stays well-formed.
     */
    private static String sanitize(@Nullable String description) {
        if (description == null) {
            return "";
        }
        int nl = description.indexOf('\n');
        String firstLine = nl < 0 ? description : description.substring(0, nl);
        return firstLine
                .replace('\'', ' ')
                .replace('[', '(')
                .replace(']', ')')
                .replace(":", " ")
                .strip();
    }
}
