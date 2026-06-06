package io.github.p4suta.tateyokopdf.cli;

import io.github.p4suta.shared.cli.InputResolver;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.OpeningSide;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.util.Locale;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * The fully parsed and validated CLI intent: which inputs to convert and how.
 *
 * <p>Keeps argument interpretation — strings to domain enums, the {@code --first-page}/direction
 * mapping — out of {@link SpreadCommand}, which is then left with parsing dispatch and conversion
 * orchestration only.
 */
record CliArguments(
        InputResolver.Resolved inputs,
        @Nullable String outputOpt,
        ReadingDirection direction,
        FirstPageMode firstPageMode,
        boolean pdfA,
        boolean lowMemory,
        boolean force) {

    /**
     * Interprets a parsed {@link CommandLine} into validated intent. The caller must have already
     * handled {@code --help}/{@code --version} and the no-positionals case.
     */
    static CliArguments from(CommandLine cmd) throws ParseException {
        @Nullable String directionValue = cmd.getOptionValue("direction");
        ReadingDirection direction =
                parseDirection(directionValue != null ? directionValue : "RTL");
        FirstPageMode firstPageMode = resolveFirstPage(cmd.getOptionValue("first-page"), direction);
        return new CliArguments(
                InputResolver.resolve(cmd.getArgList(), InputResolver.globFilter("*.pdf")),
                cmd.getOptionValue("output"),
                direction,
                firstPageMode,
                cmd.hasOption("pdf-a"),
                cmd.hasOption("low-memory"),
                cmd.hasOption("force"));
    }

    /** The PDFBox stream-cache mode implied by {@code --low-memory}. */
    MemoryMode memoryMode() {
        return lowMemory ? MemoryMode.SCRATCH_FILE : MemoryMode.IN_MEMORY;
    }

    private static ReadingDirection parseDirection(String value) throws ParseException {
        try {
            return ReadingDirection.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParseException("invalid direction '" + value + "' (expected RTL or LTR)");
        }
    }

    /**
     * Resolves the opening mode from {@code --first-page} (an absolute side or {@code cover}) given
     * the reading direction. An absolute side equals {@code STANDARD} when it falls on the
     * direction's leading side (RTL→right, LTR→left); the opposite side requests a leading blank.
     * Nothing specified → {@code STANDARD}.
     */
    private static FirstPageMode resolveFirstPage(
            @Nullable String firstPage, ReadingDirection direction) throws ParseException {
        if (firstPage == null) {
            return FirstPageMode.STANDARD;
        }
        return switch (firstPage.toLowerCase(Locale.ROOT)) {
            case "cover" -> FirstPageMode.COVER;
            case "right" -> FirstPageMode.fromSide(OpeningSide.RIGHT, direction);
            case "left" -> FirstPageMode.fromSide(OpeningSide.LEFT, direction);
            default ->
                    throw new ParseException(
                            "invalid first-page '"
                                    + firstPage
                                    + "' (expected right, left, or cover)");
        };
    }
}
