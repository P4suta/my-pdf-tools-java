package io.github.p4suta.shared.kernel;

import java.util.Arrays;

/**
 * Upper-median selection over a primitive array: the element at sorted index {@code n / 2}, which
 * for an even count is the upper of the two middle elements (not the averaging "true" median).
 * Sorts a {@code clone()}, so the input is not mutated. Assumes a non-empty array — callers guard;
 * an empty array throws {@link ArrayIndexOutOfBoundsException}.
 */
public final class Medians {

    private Medians() {}

    /** Upper median of {@code values}; assumed non-empty. */
    public static int upperMedian(int[] values) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /** Upper median of {@code values}; assumed non-empty. */
    public static long upperMedian(long[] values) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
