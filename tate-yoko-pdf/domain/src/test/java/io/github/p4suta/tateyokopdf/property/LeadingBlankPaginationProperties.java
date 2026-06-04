package io.github.p4suta.tateyokopdf.property;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.PagePairSpec;
import io.github.p4suta.tateyokopdf.domain.model.SpreadHalf;
import io.github.p4suta.tateyokopdf.domain.pagination.Pagination;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

final class LeadingBlankPaginationProperties {

    private static List<PagePairSpec> paginate(int n) {
        return Pagination.paginate(FirstPageMode.LEADING_BLANK, n);
    }

    @Property
    void firstIsAlwaysTrailingSingleOfPageZero(@ForAll @IntRange(min = 1, max = 500) int n) {
        assertThat(paginate(n).get(0)).isEqualTo(new PagePairSpec.Single(0, SpreadHalf.TRAILING));
    }

    @Property
    void everyPageAppearsExactlyOnce(@ForAll @IntRange(min = 1, max = 500) int n) {
        List<PagePairSpec> result = paginate(n);
        Set<Integer> seen = new HashSet<>();
        for (PagePairSpec p : result) {
            switch (p) {
                case PagePairSpec.Pair pair -> {
                    assertThat(seen.add(pair.firstIndex())).isTrue();
                    assertThat(seen.add(pair.secondIndex())).isTrue();
                }
                case PagePairSpec.Single single ->
                        assertThat(seen.add(single.pageIndex())).isTrue();
            }
        }
        assertThat(seen).hasSize(n);
    }

    @Property
    void onlyPageZeroIsATrailingSingle(@ForAll @IntRange(min = 1, max = 500) int n) {
        List<PagePairSpec> result = paginate(n);
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) instanceof PagePairSpec.Single single
                    && single.half() == SpreadHalf.TRAILING) {
                assertThat(i).as("trailing single only at position 0").isZero();
            }
        }
    }

    @Property
    void singlesAppearOnlyAtPosition0AndOptionallyLast(
            @ForAll @IntRange(min = 1, max = 500) int n) {
        List<PagePairSpec> result = paginate(n);
        int singles = 0;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) instanceof PagePairSpec.Single) {
                assertThat(i == 0 || i == result.size() - 1).as("single at %d", i).isTrue();
                singles++;
            }
        }
        if (n % 2 == 0 && n > 1) {
            assertThat(singles).isEqualTo(2); // page 0 (leading blank) + trailing leftover
        } else {
            assertThat(singles).isEqualTo(1);
        }
    }
}
