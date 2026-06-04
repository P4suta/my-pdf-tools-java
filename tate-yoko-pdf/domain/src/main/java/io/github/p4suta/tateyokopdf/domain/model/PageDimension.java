package io.github.p4suta.tateyokopdf.domain.model;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.Validators;

/**
 * A source page's displayed size in points (1/72 inch). Both dimensions must be positive.
 *
 * @param widthPt the width in points
 * @param heightPt the height in points
 */
public record PageDimension(float widthPt, float heightPt) {

    public PageDimension {
        Validators.requirePositive(widthPt, ErrorKind.INVALID_PARAMETER, "widthPt");
        Validators.requirePositive(heightPt, ErrorKind.INVALID_PARAMETER, "heightPt");
    }

    public static PageDimension max(PageDimension a, PageDimension b) {
        return new PageDimension(Math.max(a.widthPt, b.widthPt), Math.max(a.heightPt, b.heightPt));
    }
}
