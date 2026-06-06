package io.github.p4suta.tateyokopdf.domain.pagination;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.Validators;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.PagePairSpec;
import io.github.p4suta.tateyokopdf.domain.model.SpreadHalf;
import java.util.ArrayList;
import java.util.List;

/**
 * Pairs a document's pages into the sequence of spreads to emit, for a given {@link FirstPageMode}.
 *
 * <p>One exhaustive {@code switch} over the three openings: {@code STANDARD} pairs from the first
 * ({@code 1·2, 3·4, …}); {@code COVER} isolates page 1 as a standalone cover on the reading-leading
 * half, then pairs from page 2 ({@code [1], 2·3, …}); {@code LEADING_BLANK} opens with page 1 on
 * the trailing half — across from an implied leading blank — then pairs from page 2 ({@code [▢|1],
 * 2·3, …}). The two offset modes share their grouping; only page 0's half (and, for STANDARD, its
 * presence) differs.
 */
public final class Pagination {

    private Pagination() {}

    /**
     * Pairs the source pages into the spreads to emit, in output order, for {@code mode}.
     *
     * @param mode how page 1 opens
     * @param totalPages the number of pages in the source ({@code >= 1})
     * @return the spreads to emit
     */
    public static List<PagePairSpec> paginate(FirstPageMode mode, int totalPages) {
        requirePages(totalPages);

        List<PagePairSpec> result = new ArrayList<>();
        int start =
                switch (mode) {
                    case STANDARD -> 0;
                    case COVER -> {
                        result.add(new PagePairSpec.Single(0, SpreadHalf.LEADING));
                        yield 1;
                    }
                    case LEADING_BLANK -> {
                        result.add(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
                        yield 1;
                    }
                };
        pairFrom(result, start, totalPages);
        return List.copyOf(result);
    }

    /** Guards the page count every mode requires before paginating. */
    private static void requirePages(int totalPages) {
        Validators.require(totalPages > 0, ErrorKind.PDF_INVALID_PAGE, "totalPages=" + totalPages);
    }

    /**
     * Appends spreads covering the page range {@code [start, totalPages)} by pairing adjacent pages
     * ({@code start·start+1}, {@code start+2·start+3}, …). A lone trailing page (odd-length range)
     * becomes a leading {@link PagePairSpec.Single}.
     */
    private static void pairFrom(List<PagePairSpec> out, int start, int totalPages) {
        for (int i = start; i < totalPages; i += 2) {
            out.add(
                    i + 1 < totalPages
                            ? new PagePairSpec.Pair(i, i + 1)
                            : new PagePairSpec.Single(i));
        }
    }
}
