package io.github.p4suta.shared.kernel;

import java.util.Arrays;

/**
 * Upper-median selection over a primitive array, shared across the apps. For an array of {@code n}
 * elements this returns the element at sorted index {@code n / 2} — for an even {@code n} that is
 * the <strong>upper</strong> of the two middle elements (e.g. {@code {1, 2, 3, 4} -> 3}), and for
 * an odd {@code n} the single middle element. This is the exact {@code clone() / Arrays.sort /
 * [length / 2]} idiom the register and despeckle domains hand-roll today, factored out so their
 * existing values are reproduced bit-for-bit.
 *
 * <p>The input array is never mutated — a {@code clone()} is sorted instead.
 *
 * <p>Each method <strong>ASSUMES the array is non-empty</strong>; callers guard against empty
 * input. An empty array yields {@code sorted[0]}, which throws {@link
 * ArrayIndexOutOfBoundsException}.
 *
 * <p>This is deliberately the upper-median, <em>not</em> the averaging "true" median: the two
 * differ for even counts, and the callers being unified depend on the upper-median value.
 */
public final class Medians {

    private Medians() {}

    /**
     * The upper median of {@code values}: the element at sorted index {@code values.length / 2}.
     * For an even count this is the upper of the two middle elements.
     *
     * @param values the values to take the median of; ASSUMED non-empty (callers guard)
     * @return the upper-median element
     */
    public static int upperMedian(int[] values) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /**
     * The upper median of {@code values}: the element at sorted index {@code values.length / 2}.
     * For an even count this is the upper of the two middle elements.
     *
     * @param values the values to take the median of; ASSUMED non-empty (callers guard)
     * @return the upper-median element
     */
    public static long upperMedian(long[] values) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
