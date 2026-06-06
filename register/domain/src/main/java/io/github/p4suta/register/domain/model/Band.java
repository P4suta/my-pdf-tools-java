package io.github.p4suta.register.domain.model;

/**
 * A half-open index range {@code [start, endExclusive)} into a 1-D ink histogram (a row or column
 * projection of a page). Carried by a {@link Detection} as the band its column was found in.
 */
public record Band(int start, int endExclusive) {

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
