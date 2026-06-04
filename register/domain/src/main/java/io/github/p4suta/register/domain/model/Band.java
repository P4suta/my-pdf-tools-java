package io.github.p4suta.register.domain.model;

/**
 * A half-open index range {@code [start, endExclusive)} into a 1-D ink histogram (a row or column
 * projection of a page). Produced by the projection-profile reductions and carried by a {@link
 * Detection} as the vertical band its column was found in.
 *
 * @param start the first index in the band (inclusive)
 * @param endExclusive one past the last index in the band (exclusive)
 */
public record Band(int start, int endExclusive) {

    /** Validates the range. */
    public Band {
        if (endExclusive <= start) {
            throw new IllegalArgumentException("empty band: [" + start + ", " + endExclusive + ")");
        }
    }

    /** The number of indices in the band. */
    public int length() {
        return endExclusive - start;
    }
}
