package io.github.p4suta.tateyokopdf.domain.pagination;

import static io.github.p4suta.tateyokopdf.domain.model.FirstPageMode.COVER;
import static io.github.p4suta.tateyokopdf.domain.model.FirstPageMode.LEADING_BLANK;
import static io.github.p4suta.tateyokopdf.domain.model.FirstPageMode.STANDARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.PagePairSpec;
import io.github.p4suta.tateyokopdf.domain.model.SpreadHalf;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Parity tests for the consolidated {@link Pagination#paginate(FirstPageMode, int)} entry point.
 *
 * <p>Folds in the former {@code StandardPaginationTest}, {@code CoverSinglePaginationTest}, {@code
 * LeadingBlankPaginationTest}, and {@code PaginationStrategyFactoryTest} (whose mode-selection
 * intent is now the per-mode first-element distinctions in {@link Standard}, {@link Cover}, and
 * {@link LeadingBlank}). Expected outputs are unchanged — they are the parity gate.
 */
final class PaginationTest {

    @Nested
    final class Standard {

        @Test
        void singlePageProducesOneSingle() {
            assertThat(Pagination.paginate(STANDARD, 1))
                    .containsExactly(new PagePairSpec.Single(0));
        }

        @Test
        void twoPagesProduceOnePair() {
            assertThat(Pagination.paginate(STANDARD, 2))
                    .containsExactly(new PagePairSpec.Pair(0, 1));
        }

        @Test
        void evenPagesAllPaired() {
            assertThat(Pagination.paginate(STANDARD, 4))
                    .containsExactly(new PagePairSpec.Pair(0, 1), new PagePairSpec.Pair(2, 3));
        }

        @Test
        void oddPagesEndWithSingle() {
            assertThat(Pagination.paginate(STANDARD, 5))
                    .containsExactly(
                            new PagePairSpec.Pair(0, 1),
                            new PagePairSpec.Pair(2, 3),
                            new PagePairSpec.Single(4));
        }

        @Test
        void largeInputHandled() {
            List<PagePairSpec> result = Pagination.paginate(STANDARD, 1000);
            assertThat(result).hasSize(500);
            assertThat(result.get(0)).isEqualTo(new PagePairSpec.Pair(0, 1));
            assertThat(result.get(499)).isEqualTo(new PagePairSpec.Pair(998, 999));
        }
    }

    @Nested
    final class Cover {

        @Test
        void singlePageProducesOnlyCover() {
            assertThat(Pagination.paginate(COVER, 1)).containsExactly(new PagePairSpec.Single(0));
        }

        @Test
        void twoPagesProduceCoverPlusSingle() {
            assertThat(Pagination.paginate(COVER, 2))
                    .containsExactly(new PagePairSpec.Single(0), new PagePairSpec.Single(1));
        }

        @Test
        void fivePagesProduceCoverThenPaired() {
            assertThat(Pagination.paginate(COVER, 5))
                    .containsExactly(
                            new PagePairSpec.Single(0),
                            new PagePairSpec.Pair(1, 2),
                            new PagePairSpec.Pair(3, 4));
        }

        @Test
        void sixPagesProduceCoverThenPairedThenSingle() {
            assertThat(Pagination.paginate(COVER, 6))
                    .containsExactly(
                            new PagePairSpec.Single(0),
                            new PagePairSpec.Pair(1, 2),
                            new PagePairSpec.Pair(3, 4),
                            new PagePairSpec.Single(5));
        }

        @Test
        void coverIsAlwaysFirst() {
            for (int n : new int[] {1, 2, 3, 4, 5, 6, 10, 99}) {
                assertThat(Pagination.paginate(COVER, n).get(0))
                        .isEqualTo(new PagePairSpec.Single(0));
            }
        }
    }

    @Nested
    final class LeadingBlank {

        @Test
        void singlePageProducesTrailingSingle() {
            assertThat(Pagination.paginate(LEADING_BLANK, 1))
                    .containsExactly(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
        }

        @Test
        void twoPagesProduceTrailingThenLeadingSingle() {
            assertThat(Pagination.paginate(LEADING_BLANK, 2))
                    .containsExactly(
                            new PagePairSpec.Single(0, SpreadHalf.TRAILING),
                            new PagePairSpec.Single(1, SpreadHalf.LEADING));
        }

        @Test
        void fivePagesLeadWithBlankThenPaired() {
            assertThat(Pagination.paginate(LEADING_BLANK, 5))
                    .containsExactly(
                            new PagePairSpec.Single(0, SpreadHalf.TRAILING),
                            new PagePairSpec.Pair(1, 2),
                            new PagePairSpec.Pair(3, 4));
        }

        @Test
        void sixPagesLeadWithBlankThenPairedThenTrailingLeftover() {
            assertThat(Pagination.paginate(LEADING_BLANK, 6))
                    .containsExactly(
                            new PagePairSpec.Single(0, SpreadHalf.TRAILING),
                            new PagePairSpec.Pair(1, 2),
                            new PagePairSpec.Pair(3, 4),
                            new PagePairSpec.Single(5, SpreadHalf.LEADING));
        }

        @Test
        void firstPageIsAlwaysATrailingSingle() {
            for (int n : new int[] {1, 2, 3, 4, 5, 6, 10, 99}) {
                assertThat(Pagination.paginate(LEADING_BLANK, n).get(0))
                        .isEqualTo(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
            }
        }

        @Test
        void mirrorsCoverExceptForPageZerosHalf() {
            // The two offset modes share their grouping; only page 0's half differs.
            List<PagePairSpec> leadingBlank = Pagination.paginate(LEADING_BLANK, 7);
            List<PagePairSpec> cover = Pagination.paginate(COVER, 7);
            assertThat(leadingBlank.subList(1, leadingBlank.size()))
                    .isEqualTo(cover.subList(1, cover.size()));
            assertThat(leadingBlank.get(0))
                    .isEqualTo(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
            assertThat(cover.get(0)).isEqualTo(new PagePairSpec.Single(0, SpreadHalf.LEADING));
        }
    }

    /**
     * Mode-selection parity (folded in from the deleted factory test): each mode yields its
     * distinct opening element, so {@code paginate} dispatches on {@link FirstPageMode} correctly.
     */
    @Nested
    final class ModeSelection {

        @Test
        void standardOpensWithAPair() {
            assertThat(Pagination.paginate(STANDARD, 4).get(0))
                    .isEqualTo(new PagePairSpec.Pair(0, 1));
        }

        @Test
        void coverOpensWithALeadingSingle() {
            assertThat(Pagination.paginate(COVER, 4).get(0))
                    .isEqualTo(new PagePairSpec.Single(0, SpreadHalf.LEADING));
        }

        @Test
        void leadingBlankOpensWithATrailingSingle() {
            assertThat(Pagination.paginate(LEADING_BLANK, 4).get(0))
                    .isEqualTo(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
        }
    }

    /** Page-count guard and immutability hold for every mode. */
    @Nested
    final class Guards {

        @ParameterizedTest
        @EnumSource(FirstPageMode.class)
        void zeroRejected(FirstPageMode mode) {
            assertThatThrownBy(() -> Pagination.paginate(mode, 0))
                    .isInstanceOfSatisfying(
                            SpreadException.class,
                            ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_INVALID_PAGE));
        }

        @ParameterizedTest
        @EnumSource(FirstPageMode.class)
        void negativeRejected(FirstPageMode mode) {
            assertThatThrownBy(() -> Pagination.paginate(mode, -5))
                    .isInstanceOf(SpreadException.class);
        }

        @ParameterizedTest
        @EnumSource(FirstPageMode.class)
        void resultIsImmutable(FirstPageMode mode) {
            List<PagePairSpec> result = Pagination.paginate(mode, 2);
            assertThatThrownBy(() -> result.add(new PagePairSpec.Single(99)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
