package io.github.p4suta.shared.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jspecify.annotations.Nullable;

/**
 * Renders a section-1 man page (troff) for a Commons CLI command from the same {@link Options}
 * model {@code --help} uses, so the two never drift. Output is written to stdout by the CLI's
 * {@code --man} flag and can be redirected to {@code <program>.1} for installation under {@code
 * man1/}.
 */
public final class ManPage {

    private ManPage() {}

    /**
     * {@return the troff source of the man page}
     *
     * @param program the command name (e.g. {@code pdfbook})
     * @param version the version string shown in the page footer
     * @param synopsis the usage syntax line
     * @param description the description paragraph (the {@code --help} header)
     * @param options the command's option model
     */
    public static String troff(
            String program, String version, String synopsis, String description, Options options) {
        StringBuilder out = new StringBuilder();
        out.append(".TH ")
                .append(program.toUpperCase(java.util.Locale.ROOT))
                .append(" 1 \"\" \"")
                .append(escape(program + " " + version))
                .append("\" \"User Commands\"\n");
        out.append(".SH NAME\n")
                .append(escape(program))
                .append(" \\- ")
                .append(firstSentence(description))
                .append('\n');
        out.append(".SH SYNOPSIS\n").append(escape(synopsis)).append('\n');
        out.append(".SH DESCRIPTION\n").append(escape(description)).append('\n');
        out.append(".SH OPTIONS\n");
        for (Option o : options.getOptions()) {
            out.append(".TP\n").append(optionHeader(o)).append('\n');
            out.append(escape(collapse(o.getDescription()))).append('\n');
        }
        return out.toString();
    }

    private static String optionHeader(Option o) {
        StringBuilder head = new StringBuilder();
        if (o.getOpt() != null) {
            head.append("\\-").append(o.getOpt());
        }
        if (o.getLongOpt() != null) {
            if (head.length() > 0) {
                head.append(", ");
            }
            head.append("\\-\\-").append(o.getLongOpt());
        }
        if (o.hasArg()) {
            head.append(" \\fI")
                    .append(o.getArgName() == null ? "arg" : o.getArgName())
                    .append("\\fR");
        }
        return head.toString();
    }

    private static String firstSentence(String text) {
        String collapsed = collapse(text);
        int dot = collapsed.indexOf(". ");
        return escape(dot < 0 ? collapsed : collapsed.substring(0, dot));
    }

    /**
     * Collapses internal whitespace runs (the help header is one wrapped paragraph) to single
     * spaces.
     */
    private static String collapse(@Nullable String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    /** troff escaping: a leading dot would start a request, and backslash is the escape char. */
    private static String escape(String text) {
        String escaped = text.replace("\\", "\\\\");
        return escaped.startsWith(".") ? "\\&" + escaped : escaped;
    }
}
