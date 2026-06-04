package io.github.p4suta.register.domain.model;

import io.github.p4suta.shared.kernel.Validators;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Per-run registration knobs.
 *
 * @param dpi the output resolution; this fixes the canvas pixel size for the chosen paper, so it is
 *     a single run-wide value (not per page). Empty means the run inherits the inputs' own scan
 *     resolution (the registration service resolves it before rendering), falling back to {@link
 *     #DEFAULT_DPI} only when the inputs carry none
 * @param paper the target paper size, or null to auto-detect it from the scanned page size (the
 *     registration service resolves it before rendering, via {@link PaperSize#fromScan})
 * @param deskew whether to straighten each page before detection
 * @param scale whether to scale each page's column to the reference height
 * @param outlierRatio a column smaller than this fraction of the reference area is centered, not
 *     registered (must be in {@code (0, 1]})
 * @param anchor where to place the main column on the canvas
 */
public record RegisterOptions(
        OptionalInt dpi,
        @Nullable PaperSize paper,
        boolean deskew,
        boolean scale,
        double outlierRatio,
        Anchor anchor) {

    /** The output resolution assumed when none is given. */
    public static final int DEFAULT_DPI = 400;

    /** Validates the option values. */
    public RegisterOptions {
        if (dpi.isPresent()) {
            Validators.requirePositive(dpi.getAsInt(), "dpi");
        }
        if (outlierRatio <= 0 || outlierRatio > 1) {
            throw new IllegalArgumentException("outlierRatio must be in (0, 1]: " + outlierRatio);
        }
    }

    /** The resolution that sizes the canvas and is stamped on every output page. */
    public int canvasDpi() {
        return dpi.isPresent() ? dpi.getAsInt() : DEFAULT_DPI;
    }
}
