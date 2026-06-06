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

    private static String bash(String program, Options options, List<String> subcommands) {
        String flags =
                options.getOptions().stream()
                        .flatMap(o -> flagTokens(o).stream())
                        .collect(Collectors.joining(" "));
        String subs = String.join(" ", subcommands);
        String fn = "_" + program.replace('-', '_');
        return """
        # bash completion for %1$s — source this file or drop it in a bash-completion.d directory.
        %2$s() {
          local cur="${COMP_WORDS[COMP_CWORD]}"
          local opts="%3$s"
          local subs="%4$s"
          if [[ "$cur" == -* ]]; then
            COMPREPLY=( $(compgen -W "$opts" -- "$cur") )
            return
          fi
          COMPREPLY=( $(compgen -W "$subs" -f -- "$cur") )
        }
        complete -F %2$s %1$s
        """
                .formatted(program, fn, flags, subs);
    }

    private static String zsh(String program, Options options, List<String> subcommands) {
        String optLines =
                options.getOptions().stream()
                        .map(ShellCompletion::zshOptionLine)
                        .collect(Collectors.joining("\n"));
        String subLine =
                subcommands.isEmpty() ? "" : "\n  '1: :(" + String.join(" ", subcommands) + ")' \\";
        return """
        #compdef %1$s
        # zsh completion for %1$s
        _%2$s() {
          _arguments -s \\
        %3$s%4$s
            '*:file:_files'
        }
        _%2$s "$@"
        """
                .formatted(program, program.replace('-', '_'), optLines, subLine);
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
