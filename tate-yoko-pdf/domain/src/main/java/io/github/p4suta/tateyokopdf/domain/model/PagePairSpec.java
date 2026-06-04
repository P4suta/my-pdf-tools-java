package io.github.p4suta.tateyokopdf.domain.model;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.Validators;

/**
 * One unit of pagination: either two source pages destined for one spread, or a lone page on one
 * half. The sealed variants let the layout calculator and writer dispatch exhaustively.
 */
public sealed interface PagePairSpec {

    /** Two consecutive source pages sharing a spread, by zero-based index. */
    record Pair(int firstIndex, int secondIndex) implements PagePairSpec {
        public Pair {
            Validators.requireNonNegative(firstIndex, ErrorKind.PDF_INVALID_PAGE, "firstIndex");
            Validators.requireNonNegative(secondIndex, ErrorKind.PDF_INVALID_PAGE, "secondIndex");
        }
    }

    record Single(int pageIndex, SpreadHalf half) implements PagePairSpec {
        public Single {
            Validators.requireNonNegative(pageIndex, ErrorKind.PDF_INVALID_PAGE, "pageIndex");
            Validators.requireNonNull(half, ErrorKind.INVALID_PARAMETER, "half");
        }

        /** A lone page on the reading-leading half (standalone cover or odd trailing page). */
        public Single(int pageIndex) {
            this(pageIndex, SpreadHalf.LEADING);
        }
    }
}
