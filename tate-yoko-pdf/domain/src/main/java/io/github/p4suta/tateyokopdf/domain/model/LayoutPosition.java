package io.github.p4suta.tateyokopdf.domain.model;

/**
 * Where a page's lower-left corner sits within the spread frame, in points from the frame's origin.
 *
 * @param offsetXPt the horizontal offset in points
 * @param offsetYPt the vertical offset in points
 */
public record LayoutPosition(float offsetXPt, float offsetYPt) {}
