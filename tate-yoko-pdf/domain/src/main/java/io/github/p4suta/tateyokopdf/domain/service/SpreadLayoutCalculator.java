package io.github.p4suta.tateyokopdf.domain.service;

import io.github.p4suta.tateyokopdf.domain.model.*;
import java.util.Optional;

/**
 * Positions source pages within a fixed two-page spread frame. Paired and single spreads share one
 * placement routine: a page is centered within one half and shifted onto the leading or trailing
 * side, where the side↔direction mapping lives solely in {@link #onRightHalf}.
 */
public class SpreadLayoutCalculator {

    /**
     * A spread of two facing pages: {@code first} on the leading half, {@code second} on the
     * trailing half (RTL → first right/second left; LTR → mirrored).
     */
    public SpreadLayout calculate(
            ReadingDirection direction, PageDimension first, PageDimension second) {
        PageDimension bounds = PageDimension.max(first, second);
        return new SpreadLayout(
                spreadSpec(bounds),
                place(direction, SpreadHalf.LEADING, bounds, first),
                Optional.of(place(direction, SpreadHalf.TRAILING, bounds, second)));
    }

    /** A spread holding a single page on {@code half}; the opposite half is left blank. */
    public SpreadLayout calculateSingle(
            ReadingDirection direction, PageDimension page, SpreadHalf half) {
        return new SpreadLayout(
                spreadSpec(page), place(direction, half, page, page), Optional.empty());
    }

    /** Always a full two-page width, so single and paired spreads share one frame size. */
    private static SpreadSpec spreadSpec(PageDimension bounds) {
        return new SpreadSpec(bounds.widthPt() * 2f, bounds.heightPt());
    }

    /**
     * Centers {@code page} within one half (each half is {@code bounds.widthPt} wide) and shifts it
     * onto the requested half; vertical position is centered against {@code bounds} too.
     */
    private static LayoutPosition place(
            ReadingDirection direction, SpreadHalf half, PageDimension bounds, PageDimension page) {
        float halfWidth = bounds.widthPt();
        float x = (halfWidth - page.widthPt()) / 2f;
        if (onRightHalf(direction, half)) {
            x += halfWidth;
        }
        float y = (bounds.heightPt() - page.heightPt()) / 2f;
        return new LayoutPosition(x, y);
    }

    /** The right half is RTL's leading side and LTR's trailing side. */
    private static boolean onRightHalf(ReadingDirection direction, SpreadHalf half) {
        return (direction == ReadingDirection.RTL) == (half == SpreadHalf.LEADING);
    }
}
