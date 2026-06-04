package io.github.p4suta.register.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * One page's recorded pipeline state, captured during a {@code --diag} run: the skew that was
 * measured and whether it was corrected, the detected main column with its band, the per-parity
 * reference the page was aligned to, and exactly where the page landed on the canvas — including
 * whether any scan margin was cropped at the canvas edge to reach that placement.
 *
 * <p>A pure domain value type, so the registrar can describe its own state without depending on the
 * diagnostics subsystem; the registrar returns it and the reporter adapter consumes it. Holds only
 * small scalars and boxes — projection profiles are re-derived at draw time — so a whole corpus'
 * worth fits comfortably in memory for the end-of-run report.
 *
 * @param index 0-based page index in reading order
 * @param parity which side of the spread the page is
 * @param source the source file name
 * @param workWidth the analyzed (deskewed) page width in pixels
 * @param workHeight the analyzed (deskewed) page height in pixels
 * @param canvasWidth the output canvas width in pixels
 * @param canvasHeight the output canvas height in pixels
 * @param deskewEnabled whether deskew was on for this run
 * @param skew the skew estimate, or null when deskew was off
 * @param column the detected main column, or null when none was found
 * @param referenceBox the parity reference box, or null when the corpus had no reference
 * @param placement how the page was placed on the canvas
 */
public record PageDiagnostic(
        int index,
        Parity parity,
        String source,
        int workWidth,
        int workHeight,
        int canvasWidth,
        int canvasHeight,
        boolean deskewEnabled,
        @Nullable Skew skew,
        @Nullable Column column,
        @Nullable Box referenceBox,
        Placement placement) {

    // The page's skew reading is the shared top-level Skew value type (same package).

    /**
     * The detected main column in work-page pixel coordinates, plus the row-projection band its
     * vertical extent came from.
     *
     * @param box the column bounding box
     * @param bandStart the band's first row (inclusive)
     * @param bandEnd the band's last row (exclusive)
     */
    public record Column(Box box, int bandStart, int bandEnd) {}

    /**
     * How a page landed on the canvas: the planner's intended translation, the actual placement,
     * and flags for whether any content was cropped at the canvas edge. A reference-anchored page
     * is placed exactly at {@code (intendedDx, intendedDy)} and its overflowing scan margin is
     * cropped; a centered or outlier page is clamped to stay whole, so {@code placedX/placedY} may
     * differ from the intended offset and {@code croppedX/croppedY} are false unless the page is
     * larger than the canvas.
     *
     * @param detected whether a column was detected and a reference existed (else the page was
     *     centered as-is)
     * @param passthrough whether detection was an outlier, so the whole page was centered unscaled
     * @param scale the isotropic scale applied (1.0 = none)
     * @param intendedDx the planner's intended horizontal translation
     * @param intendedDy the planner's intended vertical translation
     * @param placedX the actual horizontal placement
     * @param placedY the actual vertical placement
     * @param croppedX whether content overflowed (was cropped at) a left/right canvas edge
     * @param croppedY whether content overflowed (was cropped at) a top/bottom canvas edge
     * @param contentWidth the placed content width in pixels (after any scaling)
     * @param contentHeight the placed content height in pixels (after any scaling)
     */
    public record Placement(
            boolean detected,
            boolean passthrough,
            double scale,
            int intendedDx,
            int intendedDy,
            int placedX,
            int placedY,
            boolean croppedX,
            boolean croppedY,
            int contentWidth,
            int contentHeight) {}
}
