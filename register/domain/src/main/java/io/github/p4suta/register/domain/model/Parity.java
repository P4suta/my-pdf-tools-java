package io.github.p4suta.register.domain.model;

/**
 * Which side of a spread a page is. In a book read right-to-left, the first page (index 0) is a
 * recto; pages then alternate. Recto and verso text columns sit at mirror-image positions, so each
 * parity gets its own reference layout.
 */
public enum Parity {
    /** A right-hand page (even page index). */
    RECTO,
    /** A left-hand page (odd page index). */
    VERSO;

    /** The parity of the page at {@code pageIndex} (0-based, in reading order). */
    public static Parity of(int pageIndex) {
        return (pageIndex & 1) == 0 ? RECTO : VERSO;
    }
}
