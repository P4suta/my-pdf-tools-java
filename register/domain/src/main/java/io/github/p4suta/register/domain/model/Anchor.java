package io.github.p4suta.register.domain.model;

/** Where a registered page's main column is placed on the canvas. */
public enum Anchor {
    /**
     * Put the column's center at the canvas center — balanced margins, clamped so the whole page
     * always stays on the canvas (nothing is cropped). Each page is centered on its own; it is not
     * pinned to the corpus, so the text block's absolute position still drifts page to page with
     * however much ink each page carries.
     */
    CENTER,
    /**
     * Register the page onto the corpus type-area grid — the fixed rectangle the book is set in
     * (the default). On each axis the page's text is flush to one grid edge and floats off the
     * other, so the grid-flush edge is anchored: a body page by its top (the head margin) and right
     * (the reading origin of vertical right-to-left text); an opener by its bottom (when the head
     * is dropped) or left (when the rightmost columns are blank). So the text block — and, riding
     * along with it, the running title and page number — lands at the same canvas position on every
     * page of a parity. The scan margin that overflows the canvas is cropped, which is what makes a
     * fixed text-block position reachable.
     */
    TOP_RIGHT
}
