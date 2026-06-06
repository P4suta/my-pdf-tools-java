package io.github.p4suta.register.domain.model;

import io.github.p4suta.shared.kernel.Validators;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Per-run registration knobs.
 *
 * @param dpi the output resolution (run-wide, not per page). Empty means inherit the inputs' own
 *     scan resolution, falling back to {@link #DEFAULT_DPI} only when the inputs carry none
 * @param paper the target paper size, or null to auto-detect from the scanned page size (via {@link
 *     PaperSize#fromScan})
 * @param outlierRatio a column smaller than this fraction of the reference area is centered, not
 *     registered (must be in {@code (0, 1]})
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
