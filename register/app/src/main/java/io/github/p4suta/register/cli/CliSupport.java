package io.github.p4suta.register.cli;

import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.PaperSize;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.shared.cli.CliOptionSupport;
import java.util.OptionalInt;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.jspecify.annotations.Nullable;

/**
 * The register-specific argument-parsing logic shared by the two CLI front ends ({@link
 * RegisterCommand} and {@code PipelineCommand}). Both parse the same registration knobs, so this
 * holds the part of that common logic that is specific to register — the {@link RegisterOptions}
 * assembly, the {@code auto}/paper-name resolution, and the nullable {@code --dpi} parse — while
 * the app-neutral int/double/enum/positional/help/usage primitives come from the shared {@link
 * CliOptionSupport}.
 */
final class CliSupport {

    /** A column smaller than this fraction of the reference area is centered, not registered. */
    static final double DEFAULT_OUTLIER_RATIO = 0.5;

    private CliSupport() {}

    /**
     * Build the registration knobs common to both commands: {@code --dpi}, {@code --paper}, {@code
     * --no-deskew}, {@code --no-scale}, {@code --outlier-ratio} and {@code --anchor}.
     *
     * @return the registration options (an absent {@code --dpi} stays empty for the runner to
     *     resolve)
     * @throws ParseException if {@code --dpi}, {@code --outlier-ratio} or {@code --anchor} is
     *     present but malformed
     */
    static RegisterOptions buildRegisterOptions(CommandLine cmd) throws ParseException {
        @Nullable Integer dpi = parseIntOpt(cmd, "dpi");
        return new RegisterOptions(
                dpi == null ? OptionalInt.empty() : OptionalInt.of(dpi),
                parsePaper(cmd.getOptionValue("paper")),
                !cmd.hasOption("no-deskew"),
                !cmd.hasOption("no-scale"),
                CliOptionSupport.parseDouble(cmd, "outlier-ratio", DEFAULT_OUTLIER_RATIO),
                CliOptionSupport.parseEnum(
                        Anchor.class, cmd.getOptionValue("anchor"), Anchor.TOP_RIGHT));
    }

    /**
     * A paper spec, or null for {@code auto} (the default) — resolved from the scan by the runner.
     */
    static @Nullable PaperSize parsePaper(@Nullable String spec) {
        if (spec == null || spec.trim().equalsIgnoreCase("auto")) {
            return null;
        }
        return PaperSize.parse(spec);
    }

    /**
     * Parses option {@code opt} as an {@code int}, returning {@code null} when it is absent.
     * Register-specific because the shared {@link CliOptionSupport#parseInt} has no nullable
     * variant and {@code --dpi} must distinguish "absent" (inherit the scan's resolution) from a
     * value.
     *
     * @param opt the option name (without the {@code --} prefix)
     * @return the parsed value, or {@code null} when the option is absent
     * @throws ParseException if the option is present but not an integer
     */
    static @Nullable Integer parseIntOpt(CommandLine cmd, String opt) throws ParseException {
        String value = cmd.getOptionValue(opt);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new ParseException("--" + opt + " must be an integer, but got: '" + value + "'");
        }
    }
}
