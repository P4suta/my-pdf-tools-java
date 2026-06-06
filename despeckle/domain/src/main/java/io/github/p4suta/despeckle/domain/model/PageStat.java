package io.github.p4suta.despeckle.domain.model;

/**
 * One page's despeckle outcome, the unit every corpus chart plots. Derived from {@link
 * ProcessResult} at {@code addPage} time, so the renderers never reach back into the raw result.
 *
 * @param stem the page path relative to the input root, without extension
 * @param componentsBefore 8-connected component count of the input
 * @param componentsAfter 8-connected component count of the output
 * @param removedRatio fraction of black pixels removed, in {@code [0, 1]}
 */
public record PageStat(
        String stem, int componentsBefore, int componentsAfter, double removedRatio) {

    /** Net drop in 8-connected components on this page. */
    public int componentsRemoved() {
        return componentsBefore - componentsAfter;
    }

    /** Black pixels removed as a percentage, rounded for display and the warning test. */
    public int removedPercent() {
        return (int) Math.round(removedRatio * 100);
    }
}
