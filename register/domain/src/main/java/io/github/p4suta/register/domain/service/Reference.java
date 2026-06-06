package io.github.p4suta.register.domain.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageObservation;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.shared.kernel.Medians;
import java.util.List;
import java.util.Map;

/**
 * The reference layout the corpus is registered onto: one main-column box per parity, taken as the
 * component-wise median across all pages of that parity. The median is robust — a single badly
 * scanned page does not bend the reference — and outliers (boxes far smaller than the median, i.e.
 * detection failures or chapter openers) are dropped before the median is taken.
 */
public record Reference(Box recto, Box verso) {

    /** The reference box for {@code parity}. */
    public Box forParity(Parity parity) {
        return parity == Parity.RECTO ? recto : verso;
    }

    /**
     * Derive the per-parity references from the analyzed corpus.
     *
     * @param observations every successfully analyzed page (must be non-empty)
     * @param outlierRatio a box smaller than this fraction of the median area is excluded
     */
    public static Reference fromObservations(
            List<PageObservation> observations, double outlierRatio) {
        if (observations.isEmpty()) {
            throw new IllegalArgumentException("cannot derive a reference from zero observations");
        }
        Map<Parity, List<Box>> byParity =
                observations.stream()
                        .collect(
                                groupingBy(
                                        PageObservation::parity,
                                        mapping(PageObservation::mainColumn, toList())));
        return new Reference(
                medianForParity(byParity, Parity.RECTO, outlierRatio, observations),
                medianForParity(byParity, Parity.VERSO, outlierRatio, observations));
    }

    private static Box medianForParity(
            Map<Parity, List<Box>> byParity,
            Parity parity,
            double outlierRatio,
            List<PageObservation> observations) {
        List<Box> pool = byParity.getOrDefault(parity, List.of());
        if (pool.isEmpty()) {
            // A corpus with only one parity (very short run): fall back to the whole set,
            // computed only here (not eagerly for every parity).
            pool = observations.stream().map(PageObservation::mainColumn).toList();
        }
        long medianArea = medianArea(pool);
        List<Box> kept = pool.stream().filter(b -> b.area() >= outlierRatio * medianArea).toList();
        if (kept.isEmpty()) {
            kept = pool;
        }
        return median(kept);
    }

    private static long medianArea(List<Box> boxes) {
        long[] areas = boxes.stream().mapToLong(Box::area).toArray();
        return Medians.upperMedian(areas);
    }

    private static Box median(List<Box> boxes) {
        int n = boxes.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] ws = new int[n];
        int[] hs = new int[n];
        for (int i = 0; i < n; i++) {
            Box b = boxes.get(i);
            xs[i] = b.x();
            ys[i] = b.y();
            ws[i] = b.w();
            hs[i] = b.h();
        }
        return new Box(
                Medians.upperMedian(xs),
                Medians.upperMedian(ys),
                Math.max(1, Medians.upperMedian(ws)),
                Math.max(1, Medians.upperMedian(hs)));
    }
}
