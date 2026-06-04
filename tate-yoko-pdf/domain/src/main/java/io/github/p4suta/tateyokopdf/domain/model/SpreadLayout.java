package io.github.p4suta.tateyokopdf.domain.model;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.Validators;
import java.util.Optional;

/**
 * The computed geometry of one spread: the frame size and where each page sits in it.
 *
 * @param spec the spread frame size
 * @param firstPosition where the first (or lone) page sits
 * @param secondPosition where the second page sits, or empty for a single-page spread
 */
public record SpreadLayout(
        SpreadSpec spec, LayoutPosition firstPosition, Optional<LayoutPosition> secondPosition) {
    public SpreadLayout {
        Validators.requireNonNull(spec, ErrorKind.INVALID_PARAMETER, "spec");
        Validators.requireNonNull(firstPosition, ErrorKind.INVALID_PARAMETER, "firstPosition");
        Validators.requireNonNull(secondPosition, ErrorKind.INVALID_PARAMETER, "secondPosition");
    }
}
