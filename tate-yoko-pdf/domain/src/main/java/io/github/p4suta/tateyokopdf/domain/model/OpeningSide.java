package io.github.p4suta.tateyokopdf.domain.model;

/**
 * The physical side page 1 opens on, independent of reading direction.
 *
 * <p>Where {@link SpreadHalf} is relative to the reading flow (leading vs trailing), an opening
 * side is absolute — the right or left edge of the spread, as a reader points at it. {@link
 * #leadingFor} bridges the two by naming the side a given reading direction starts from, which is
 * the single fact the CLI's {@code --first-page right|left} needs to map onto a {@link
 * FirstPageMode}.
 */
public enum OpeningSide {
    RIGHT,
    LEFT;

    /** The side a reading direction starts from: RTL opens on the right, LTR on the left. */
    public static OpeningSide leadingFor(ReadingDirection direction) {
        return direction == ReadingDirection.RTL ? RIGHT : LEFT;
    }
}
