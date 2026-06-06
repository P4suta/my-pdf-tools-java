package io.github.p4suta.despeckle.domain.model;

/**
 * The outcome of despeckling one page.
 *
 * @param componentsBefore 8-connected component count of the input
 * @param componentsAfter 8-connected component count of the output
 * @param blackPixelsBefore foreground pixel count of the input
 * @param blackPixelsAfter foreground pixel count of the output
 */
public record ProcessResult(
        int componentsBefore, int componentsAfter, long blackPixelsBefore, long blackPixelsAfter) {

    /**
     * A black-pixel removal ratio above this flags a possibly over-cleaned page. The single domain
     * home for the over-removal threshold the runner warns on and the report highlights.
     */
    public static final double OVER_REMOVAL_WARN_RATIO = 0.03;

    /**
     * Net drop in 8-connected components — dust removed minus any holes filled back in. Summed into
     * the run total and plotted per page in the report.
     */
    public int componentsRemoved() {
        return componentsBefore - componentsAfter;
    }

    /**
     * Fraction of the input's black pixels removed, in {@code [0, 1]}. A high value flags a page
     * where the filter may have eaten real text.
     */
    public double removedBlackPixelRatio() {
        if (blackPixelsBefore == 0) {
            return 0.0;
        }
        return (double) (blackPixelsBefore - blackPixelsAfter) / blackPixelsBefore;
    }

    /**
     * Whether {@link #removedBlackPixelRatio()} exceeds {@link #OVER_REMOVAL_WARN_RATIO}, the
     * threshold the runner logs on and the report flags.
     */
    public boolean isOverRemoval() {
        return removedBlackPixelRatio() > OVER_REMOVAL_WARN_RATIO;
    }
}
