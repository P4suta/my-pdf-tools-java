package io.github.p4suta.tateyokopdf.domain.model;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.Validators;

/**
 * The size of an output spread page in points. Always the full two-page width so single and paired
 * spreads share one frame. Both dimensions must be positive.
 *
 * @param widthPt the spread width in points
 * @param heightPt the spread height in points
 */
public record SpreadSpec(float widthPt, float heightPt) {

    public SpreadSpec {
        Validators.requirePositive(widthPt, ErrorKind.INVALID_PARAMETER, "widthPt");
        Validators.requirePositive(heightPt, ErrorKind.INVALID_PARAMETER, "heightPt");
    }
}
