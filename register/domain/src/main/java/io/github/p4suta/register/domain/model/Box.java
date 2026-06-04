package io.github.p4suta.register.domain.model;

import io.github.p4suta.shared.kernel.Validators;

/**
 * An axis-aligned pixel rectangle: the bounding box of a page's main text column. {@code (x, y)} is
 * the top-left corner; {@code w}/{@code h} are positive extents.
 */
public record Box(int x, int y, int w, int h) {

    /** Validates the extents. */
    public Box {
        Validators.requirePositive(w, "w");
        Validators.requirePositive(h, "h");
    }

    /** The exclusive right edge ({@code x + w}). */
    public int right() {
        return x + w;
    }

    /** The exclusive bottom edge ({@code y + h}). */
    public int bottom() {
        return y + h;
    }

    /** The area in pixels. */
    public long area() {
        return (long) w * h;
    }
}
