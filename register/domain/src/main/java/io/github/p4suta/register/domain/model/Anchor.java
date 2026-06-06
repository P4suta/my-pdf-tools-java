package io.github.p4suta.register.domain.model;

/** Where a registered page's main column is placed on the canvas. */
public enum Anchor {
    /**
     * Center the column on the canvas, clamped so the whole page stays on the canvas (nothing
     * cropped). Each page is centered on its own, not pinned to the corpus, so the text block's
     * absolute position drifts page to page with how much ink each page carries.
     */
    CENTER,
    /**
     * Register the page onto the corpus type-area grid (the default). On each axis the text is
     * flush to one grid edge and floats off the other; the grid-flush edge is anchored: a body page
     * by its top (head margin) and right (reading origin of vertical right-to-left text); an opener
     * by its bottom (head dropped) or left (rightmost columns blank). The text block lands at the
     * same canvas position on every page of a parity. Scan margin that overflows the canvas is
     * cropped, which makes a fixed text-block position reachable.
     */
    TOP_RIGHT
}
