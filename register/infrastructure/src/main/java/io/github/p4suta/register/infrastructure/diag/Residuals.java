package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.shared.kernel.Medians;
import java.util.Arrays;

/**
 * The per-page registration residual: how far a registered page's detected column lands from the
 * type-area grid edge it is anchored to. Shared by {@link DiagnosticReport} (the summary text) and
 * {@link ResidualChartRenderer} (the chart) so the two can never drift.
 *
 * <p>For each axis a page is anchored to whichever grid edge it sits on (right or left, top or
 * bottom), so the residual is the distance from the column's nearer edge to that reference edge, in
 * canvas pixels: near-zero means the text block is on the grid. The arithmetic mirrors how {@code
 * PageRenderer} actually places a page — the page is scaled by {@code placement.scale} and the
 * column edge lands at {@code placedX + round(columnEdge * scale)}, compared against the scaled
 * reference edge.
 */
final class Residuals {

    private Residuals() {}

    /**
     * Whether the page was registered to the grid (a column was detected and is not an outlier).
     */
    static boolean isRegistered(PageDiagnostic p) {
        return p.column() != null && p.placement().detected() && !p.placement().passthrough();
    }

    /**
     * The distance from the column's nearer vertical edge (left or right) to its reference edge.
     */
    static int horizontal(PageDiagnostic p) {
        Box c = requireColumn(p).box();
        Box ref = requireReference(p);
        double s = p.placement().scale();
        int placed = p.placement().placedX();
        int right = Math.abs(placed + scaled(c.x() + c.w(), s) - scaled(ref.x() + ref.w(), s));
        int left = Math.abs(placed + scaled(c.x(), s) - scaled(ref.x(), s));
        return Math.min(left, right);
    }

    /**
     * The distance from the column's nearer horizontal edge (top or bottom) to its reference edge.
     */
    static int vertical(PageDiagnostic p) {
        Box c = requireColumn(p).box();
        Box ref = requireReference(p);
        double s = p.placement().scale();
        int placed = p.placement().placedY();
        int top = Math.abs(placed + scaled(c.y(), s) - scaled(ref.y(), s));
        int bottom = Math.abs(placed + scaled(c.y() + c.h(), s) - scaled(ref.y() + ref.h(), s));
        return Math.min(top, bottom);
    }

    /**
     * The median of {@code values} (upper-median for an even count). The caller ensures non-empty.
     */
    static int median(int[] values) {
        return Medians.upperMedian(values);
    }

    /** The maximum of {@code values}. The caller ensures non-empty. */
    static int max(int[] values) {
        int[] v = values.clone();
        Arrays.sort(v);
        return v[v.length - 1];
    }

    private static PageDiagnostic.Column requireColumn(PageDiagnostic p) {
        PageDiagnostic.Column c = p.column();
        if (c == null) {
            throw new IllegalStateException("registered page without a column: " + p.source());
        }
        return c;
    }

    private static Box requireReference(PageDiagnostic p) {
        Box ref = p.referenceBox();
        if (ref == null) {
            throw new IllegalStateException("registered page without a reference: " + p.source());
        }
        return ref;
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }
}
