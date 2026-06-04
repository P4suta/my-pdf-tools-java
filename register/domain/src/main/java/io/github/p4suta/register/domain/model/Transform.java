package io.github.p4suta.register.domain.model;

/**
 * The geometric transform to register one page onto the canvas: an isotropic scale followed by an
 * integer translation. When {@code passthrough} is set, detection was unreliable (an outlier), so
 * the page is simply centered at {@code (dx, dy)} with no scaling.
 *
 * @param passthrough whether this is an unregistered, centered page
 * @param scale isotropic scale factor (1.0 = none)
 * @param dx horizontal translation of the (scaled) page, in pixels
 * @param dy vertical translation of the (scaled) page, in pixels
 */
public record Transform(boolean passthrough, double scale, int dx, int dy) {}
