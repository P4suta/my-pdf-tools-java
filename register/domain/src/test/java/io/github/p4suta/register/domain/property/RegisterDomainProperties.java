package io.github.p4suta.register.domain.property;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.Band;
import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageObservation;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.Transform;
import io.github.p4suta.register.domain.service.ProjectionProfile;
import io.github.p4suta.register.domain.service.Reference;
import io.github.p4suta.register.domain.service.TransformPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Structural invariants of the registration kernel that must hold for <em>every</em> input, not
 * just the hand-picked cases the example-based tests cover. These pin properties the algorithms
 * guarantee by construction — never-enlarge, in-bounds bands, outlier robustness — so a refactor
 * that quietly breaks one is caught across the whole input space.
 */
final class RegisterDomainProperties {

    private final TransformPlanner planner = new TransformPlanner();

    /**
     * The planner shrinks-to-fit but never enlarges: the scale it returns is always {@code <= 1.0},
     * so a placed page can never grow past the canvas. True regardless of page/canvas size, anchor,
     * outlier ratio or whether scaling is even enabled.
     */
    @Property
    void planNeverEnlarges(
            @ForAll @IntRange(min = 1, max = 20000) int pageWidth,
            @ForAll @IntRange(min = 1, max = 20000) int pageHeight,
            @ForAll @IntRange(min = 1, max = 20000) int canvasWidth,
            @ForAll @IntRange(min = 1, max = 20000) int canvasHeight,
            @ForAll boolean scaleEnabled,
            @ForAll Anchor anchor) {
        Box column = new Box(10, 10, 100, 200);
        Box reference = new Box(20, 20, 100, 200);
        Transform t =
                planner.plan(
                        column,
                        reference,
                        pageWidth,
                        pageHeight,
                        canvasWidth,
                        canvasHeight,
                        scaleEnabled,
                        0.5,
                        anchor);
        assertThat(t.scale()).isLessThanOrEqualTo(1.0);
    }

    /**
     * Every band {@code densestBand} returns is a valid sub-range of the histogram — {@code 0 <=
     * start < end <= n} — and a band is present exactly when the histogram carries ink (some count
     * {@code > 0}). Ties the structural shape of the result to the only thing that determines its
     * existence.
     */
    @Property
    void densestBandStaysInBoundsAndExistsIffThereIsInk(
            @ForAll @Size(min = 1, max = 200) List<@IntRange(min = 0, max = 1000) Integer> counts,
            @ForAll @IntRange(min = 1, max = 64) int gapBridge,
            @ForAll @IntRange(min = 1, max = 64) int blockingGap) {
        int[] arr = counts.stream().mapToInt(Integer::intValue).toArray();
        Optional<Band> band = ProjectionProfile.densestBand(arr, gapBridge, blockingGap);

        boolean hasInk = counts.stream().anyMatch(c -> c > 0);
        assertThat(band.isPresent()).isEqualTo(hasInk);

        band.ifPresent(
                b -> {
                    assertThat(b.start()).isGreaterThanOrEqualTo(0);
                    assertThat(b.start()).isLessThan(b.endExclusive());
                    assertThat(b.endExclusive()).isLessThanOrEqualTo(arr.length);
                });
    }

    /**
     * The reference is robust to a single arbitrarily-large outlier: given a strict majority of
     * inlier boxes (all sharing the same extents, varied only in position) plus one box that is
     * larger on every component, each reference component stays within the range the inliers span.
     *
     * <p>A huge outlier is the case that matters — small boxes are area-dropped before the median,
     * so they cannot bend it, but a huge one survives the filter and could in principle pull the
     * component-wise median up. The upper-median over a strict inlier majority resists it: with the
     * outlier the lone top element, the median index always lands on an inlier.
     */
    @Property
    void referenceResistsASingleHugeOutlier(
            @ForAll @From("inlierMajorityWithOneHugeOutlier") List<PageObservation> corpus,
            @ForAll @IntRange(min = 1, max = 100) int ratioPercent) {
        double outlierRatio = ratioPercent / 100.0; // in (0, 1]

        // The inliers are every observation but the last (the planted outlier).
        List<Box> inliers =
                corpus.subList(0, corpus.size() - 1).stream()
                        .map(PageObservation::mainColumn)
                        .toList();
        int minX = inliers.stream().mapToInt(Box::x).min().orElseThrow();
        int maxX = inliers.stream().mapToInt(Box::x).max().orElseThrow();
        int minY = inliers.stream().mapToInt(Box::y).min().orElseThrow();
        int maxY = inliers.stream().mapToInt(Box::y).max().orElseThrow();
        int w = inliers.get(0).w(); // all inliers share these extents
        int h = inliers.get(0).h();

        Reference reference = Reference.fromObservations(corpus, outlierRatio);
        Box recto = reference.forParity(Parity.RECTO);

        assertThat(recto.x()).isBetween(minX, maxX);
        assertThat(recto.y()).isBetween(minY, maxY);
        assertThat(recto.w()).isEqualTo(w);
        assertThat(recto.h()).isEqualTo(h);
    }

    /**
     * An all-recto corpus of {@code n >= 3} equal-extent inlier boxes (position varied) followed by
     * one box larger on every component. All recto, so a single parity reference is exercised and
     * the inlier majority is strict.
     */
    @Provide
    Arbitrary<List<PageObservation>> inlierMajorityWithOneHugeOutlier() {
        Arbitrary<Integer> inlierCount = Arbitraries.integers().between(3, 8);
        Arbitrary<Integer> extentW = Arbitraries.integers().between(50, 500);
        Arbitrary<Integer> extentH = Arbitraries.integers().between(50, 500);
        Arbitrary<List<Integer>> xs =
                Arbitraries.integers().between(0, 1000).list().ofMinSize(3).ofMaxSize(8);
        Arbitrary<List<Integer>> ys =
                Arbitraries.integers().between(0, 1000).list().ofMinSize(3).ofMaxSize(8);

        return Combinators.combine(inlierCount, extentW, extentH, xs, ys)
                .as(
                        (n, w, h, xList, yList) -> {
                            List<PageObservation> obs = new ArrayList<>();
                            for (int i = 0; i < n; i++) {
                                int x = xList.get(i % xList.size());
                                int y = yList.get(i % yList.size());
                                // index 2*i keeps every page recto (even index).
                                obs.add(
                                        new PageObservation(
                                                2 * i, Parity.RECTO, new Box(x, y, w, h)));
                            }
                            // One huge outlier, larger than any inlier on every component.
                            obs.add(
                                    new PageObservation(
                                            2 * n, Parity.RECTO, new Box(5000, 5000, 5000, 5000)));
                            return obs;
                        });
    }
}
