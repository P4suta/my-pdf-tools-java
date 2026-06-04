package io.github.p4suta.tateyokopdf.domain.model;

/**
 * How the document opens, after the reading direction has been factored out.
 *
 * <p>{@link #STANDARD} pairs pages from the first (1·2, 3·4, …) with page 1 on the reading-leading
 * side. {@link #COVER} isolates page 1 on the leading side, then pairs from page 2. {@link
 * #LEADING_BLANK} isolates page 1 on the trailing side (an implied blank leads it), then pairs from
 * page 2. The CLI's {@code --first-page right|left|cover} resolves to one of these given the
 * reading direction.
 */
public enum FirstPageMode {
    STANDARD,
    COVER,
    LEADING_BLANK;

    /**
     * Resolves the opening mode for placing page 1 on an absolute {@code side}, given the reading
     * {@code direction}. When {@code side} is the direction's leading side (RTL→right, LTR→left),
     * page 1 opens normally ({@link #STANDARD}); the opposite side leads with an implied blank
     * ({@link #LEADING_BLANK}). {@link #COVER} is not reachable through a side — it is requested
     * explicitly.
     */
    public static FirstPageMode fromSide(OpeningSide side, ReadingDirection direction) {
        return side == OpeningSide.leadingFor(direction) ? STANDARD : LEADING_BLANK;
    }
}
