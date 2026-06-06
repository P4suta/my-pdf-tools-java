package io.github.p4suta.register.domain.service;

import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.Transform;

/**
 * Plans the per-page {@link Transform} that places a page on the canvas.
 *
 * <p>Scaling is conservative: a page larger than the canvas is shrunk to fit (preserving aspect),
 * but a page is never enlarged — so the result can never grow past the canvas. The (scaled) page is
 * then translated to register its detected column onto the fixed corpus type-area grid per {@link
 * Anchor}: {@code CENTER} puts the column's center at the canvas center; {@code TOP_RIGHT} pins, on
 * each axis, the content edge that sits on the grid — top and right for a body page, but the bottom
 * (a dropped head) or left (blank rightmost columns) edge for an opener. A column far smaller than
 * the reference is an outlier and the whole page is centered.
 *
 * <p>The translation here is the <em>intended</em> alignment. For a {@code TOP_RIGHT}-anchored page
 * {@code PageRenderer} places it exactly here and crops the scan margin that overflows the canvas,
 * so the text block reaches the same position on every page; a centered or outlier page is instead
 * clamped to stay whole on the canvas (nothing cropped), which also bounds the damage from an
 * unreliable column detection.
 */
public final class TransformPlanner {

    /**
     * The grid-flush tolerance is the text-block size over this. It must clear column/row-detection
     * noise (a few px, so a body page keeps its top-right reading edges) yet stay below one column
     * or line (so an opener's whole-cell offset still flips the axis to its other edge). That is a
     * wide band — a book has tens of columns and lines — so the exact divisor is not delicate.
     */
    private static final int FLUSH_TOLERANCE_DIVISOR = 64;

    /**
     * Plan the transform that places {@code column}'s page on the canvas.
     *
     * @param reference the parity reference box (canvas coordinates; the {@code TOP_RIGHT} target)
     * @param scaleEnabled whether to shrink an oversized page to fit the canvas
     * @param outlierRatio a column smaller than this fraction of the reference area is centered
     */
    public Transform plan(
            Box column,
            Box reference,
            int pageWidth,
            int pageHeight,
            int canvasWidth,
            int canvasHeight,
            boolean scaleEnabled,
            double outlierRatio,
            Anchor anchor) {
        // Shrink-to-fit only: never enlarge, so the placed page can never exceed the canvas.
        double scale = 1.0;
        if (scaleEnabled) {
            double fit =
                    Math.min((double) canvasWidth / pageWidth, (double) canvasHeight / pageHeight);
            if (fit < 1.0) {
                scale = fit;
            }
        }

        if (column.area() < outlierRatio * reference.area()) {
            // Detection is unreliable on this page: center the whole (scaled) page.
            int scaledPageWidth = (int) Math.round(pageWidth * scale);
            int scaledPageHeight = (int) Math.round(pageHeight * scale);
            return new Transform(
                    true,
                    scale,
                    (canvasWidth - scaledPageWidth) / 2,
                    (canvasHeight - scaledPageHeight) / 2);
        }

        int columnX = (int) Math.round(column.x() * scale);
        int columnY = (int) Math.round(column.y() * scale);
        int columnWidth = (int) Math.round(column.w() * scale);
        int columnHeight = (int) Math.round(column.h() * scale);
        int dx;
        int dy;
        if (anchor == Anchor.TOP_RIGHT) {
            // Register onto the corpus type-area grid, whose position is fixed across the whole
            // book. The reference is in unscaled detection coordinates, so bring it into the page's
            // scaled space to compare it with the (already scaled) column edges. On each axis the
            // page's text is flush to one grid edge and floats off the other: a body page is
            // top-flush (the head margin, where every column starts) and right-flush (the reading
            // origin of vertical right-to-left text); an opener instead drops its head, or blanks
            // its rightmost columns, sitting foot- or left-flush. So on each axis anchor the
            // grid-flush edge — the one whose offset to the reference is small (scan jitter), not
            // large (a dropped head or blank columns). gridOffset prefers the body-page edge.
            dx =
                    gridOffset(
                            scaled(reference.right(), scale),
                            columnX + columnWidth,
                            scaled(reference.x(), scale),
                            columnX,
                            scaled(reference.w(), scale) / FLUSH_TOLERANCE_DIVISOR);
            dy =
                    gridOffset(
                            scaled(reference.y(), scale),
                            columnY,
                            scaled(reference.bottom(), scale),
                            columnY + columnHeight,
                            scaled(reference.h(), scale) / FLUSH_TOLERANCE_DIVISOR);
        } else { // CENTER
            dx = canvasWidth / 2 - (columnX + columnWidth / 2);
            dy = canvasHeight / 2 - (columnY + columnHeight / 2);
        }
        return new Transform(false, scale, dx, dy);
    }

    /**
     * The translation along one axis that snaps the page's {@code preferred} content edge to its
     * reference, unless the page is instead flush to the opposite grid edge — its preferred edge
     * floating a whole column (or a dropped head) inside the grid. The preferred edge wins on a tie
     * and within {@code tolerance}, so a body page keeps its top and right (reading-origin) edges;
     * only a clearly larger offset there hands the axis to the {@code other} edge.
     *
     * @param preferredRef the scaled reference position of the preferred edge (top or right)
     * @param preferredEdge the page's scaled preferred edge
     * @param otherRef the scaled reference position of the opposite edge (bottom or left)
     * @param otherEdge the page's scaled opposite edge
     * @param tolerance the offset, below one column/line, that separates jitter from a flush switch
     */
    private static int gridOffset(
            int preferredRef, int preferredEdge, int otherRef, int otherEdge, int tolerance) {
        int alignPreferred = preferredRef - preferredEdge;
        int alignOther = otherRef - otherEdge;
        return Math.abs(alignOther) + tolerance < Math.abs(alignPreferred)
                ? alignOther
                : alignPreferred;
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }
}
