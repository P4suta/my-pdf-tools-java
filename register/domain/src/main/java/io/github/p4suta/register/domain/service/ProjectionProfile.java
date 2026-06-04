package io.github.p4suta.register.domain.service;

import io.github.p4suta.register.domain.model.Band;
import java.util.Optional;

/**
 * Reductions of a 1-D ink histogram (a row or column projection of a bitonal page) used for
 * main-column detection. {@link #densestBand} finds the single densest contiguous band — right for
 * the vertical axis, where a wide gutter must split a running title (柱) from the body; {@link
 * #inkBounds} takes the full extent of the inked region — right for the horizontal axis, where a
 * chapter-interior page's body must not fragment into a sub-run.
 */
public final class ProjectionProfile {

    private ProjectionProfile() {}

    /**
     * The densest band in {@code counts}.
     *
     * <p>A position is "on" when its count reaches {@code max/8} (a fraction tuned for Japanese
     * glyphs). Short interior gaps — below {@code min(gapBridge, blockingGap)} — are bridged so a
     * paragraph break does not fragment the body; a gap at or above {@code blockingGap} is never
     * bridged, so the wide gutter that separates a running title from the body blocks the band. The
     * surviving on-run carrying the most ink wins.
     *
     * @param counts the ink histogram (per row, or per column)
     * @param gapBridge bridge interior gaps shorter than this (e.g. {@code length / 32})
     * @param blockingGap never bridge a gap this wide or wider (e.g. {@code dpi / 8})
     * @return the densest band, or empty if the histogram has no ink
     */
    public static Optional<Band> densestBand(int[] counts, int gapBridge, int blockingGap) {
        int n = counts.length;
        int threshold = onThreshold(counts);
        if (threshold == 0) {
            return Optional.empty();
        }
        int bridge = Math.min(gapBridge, blockingGap);

        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) {
            on[i] = counts[i] >= threshold;
        }
        // Bridge short interior gaps (flanked by "on" on both sides); never a gap >= bridge.
        int i = 0;
        while (i < n) {
            if (on[i]) {
                i++;
                continue;
            }
            int gapStart = i;
            while (i < n && !on[i]) {
                i++;
            }
            boolean flanked = gapStart > 0 && i < n;
            if (flanked && (i - gapStart) < bridge) {
                for (int k = gapStart; k < i; k++) {
                    on[k] = true;
                }
            }
        }
        // Pick the on-run carrying the most ink.
        long bestInk = -1;
        int bestStart = -1;
        int bestEnd = -1;
        i = 0;
        while (i < n) {
            if (!on[i]) {
                i++;
                continue;
            }
            int runStart = i;
            long ink = 0;
            while (i < n && on[i]) {
                ink += counts[i];
                i++;
            }
            if (ink > bestInk) {
                bestInk = ink;
                bestStart = runStart;
                bestEnd = i;
            }
        }
        if (bestStart < 0) {
            return Optional.empty();
        }
        return Optional.of(new Band(bestStart, bestEnd));
    }

    /**
     * The bounding range of all inked positions: from the first index reaching {@code max/8} to the
     * last, everything between included. Unlike {@link #densestBand}, a gap never splits the range,
     * so a chapter-interior page whose body column the gutter heuristic would fragment still yields
     * one band spanning the whole printed width. Sparse marginalia on the orthogonal axis are still
     * kept out by the vertical-band clip in the {@code MainColumnDetector}.
     *
     * @param counts the ink histogram (per row, or per column)
     * @return the {@code [first-on, last-on + 1)} range, or empty if the histogram has no ink
     */
    public static Optional<Band> inkBounds(int[] counts) {
        int threshold = onThreshold(counts);
        if (threshold == 0) {
            return Optional.empty();
        }
        int first = -1;
        int last = -1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] >= threshold) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        return Optional.of(new Band(first, last + 1));
    }

    /**
     * The "on" threshold: a position is inked at or above {@code max/8}; 0 when there is no ink.
     */
    private static int onThreshold(int[] counts) {
        int max = 0;
        for (int c : counts) {
            max = Math.max(max, c);
        }
        return max <= 0 ? 0 : Math.max(1, max / 8);
    }
}
