package io.github.p4suta.despeckle.domain.model;

import java.util.OptionalInt;

/**
 * The outcome of despeckling one page.
 *
 * <p>The black-pixel counts are always measured — they feed the over-removal guard. The component
 * counts are measured only when something consumes them (the HTML report): counting 8-connected
 * components is a full connected-component labeling of the page, one of the most expensive scans in
 * the whole clean, so a run with no report skips both counting passes and carries empty components
 * here.
 *
 * @param componentsBefore 8-connected component count of the input, when counted
 * @param componentsAfter 8-connected component count of the output, when counted
 * @param blackPixelsBefore foreground pixel count of the input
 * @param blackPixelsAfter foreground pixel count of the output
 */
public record ProcessResult(
        OptionalInt componentsBefore,
        OptionalInt componentsAfter,
        long blackPixelsBefore,
        long blackPixelsAfter) {

    /**
     * A black-pixel removal ratio above this flags a possibly over-cleaned page. The single domain
     * home for the over-removal threshold the runner warns on and the report highlights.
     */
    public static final double OVER_REMOVAL_WARN_RATIO = 0.03;

    /** A counted result — the shape the report path consumes. */
    public ProcessResult(
            int componentsBefore,
            int componentsAfter,
            long blackPixelsBefore,
            long blackPixelsAfter) {
        this(
                OptionalInt.of(componentsBefore),
                OptionalInt.of(componentsAfter),
                blackPixelsBefore,
                blackPixelsAfter);
    }

    /** The result of a run that skipped component counting (nothing consumes the counts). */
    public static ProcessResult withoutComponentStats(
            long blackPixelsBefore, long blackPixelsAfter) {
        return new ProcessResult(
                OptionalInt.empty(), OptionalInt.empty(), blackPixelsBefore, blackPixelsAfter);
    }

    /** Whether component counting ran (true on the report path). */
    public boolean hasComponentStats() {
        return componentsBefore.isPresent();
    }

    /**
     * Net drop in 8-connected components — dust removed minus any holes filled back in. Summed into
     * the run total and plotted per page in the report. {@code 0} when counting was skipped (see
     * {@link #hasComponentStats()}).
     */
    public int componentsRemoved() {
        return componentsBefore.orElse(0) - componentsAfter.orElse(0);
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

    /** Black pixels removed from the page — the cost-free removal measure every run carries. */
    public long blackPixelsRemoved() {
        return blackPixelsBefore - blackPixelsAfter;
    }

    /**
     * Whether {@link #removedBlackPixelRatio()} exceeds {@link #OVER_REMOVAL_WARN_RATIO}, the
     * threshold the runner logs on and the report flags.
     */
    public boolean isOverRemoval() {
        return removedBlackPixelRatio() > OVER_REMOVAL_WARN_RATIO;
    }
}
