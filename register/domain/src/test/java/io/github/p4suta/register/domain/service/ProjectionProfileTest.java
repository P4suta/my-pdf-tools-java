package io.github.p4suta.register.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.domain.model.Band;
import org.junit.jupiter.api.Test;

class ProjectionProfileTest {

    @Test
    void findsTheDenseBand() {
        int[] counts = {0, 0, 10, 10, 10, 0, 0, 0, 0, 0};
        Band band = ProjectionProfile.densestBand(counts, 1, 5).orElseThrow();
        assertEquals(2, band.start());
        assertEquals(5, band.endExclusive());
    }

    @Test
    void bridgesShortInteriorGaps() {
        // A one-wide gap between two dense runs is a paragraph break, not a boundary.
        int[] counts = {10, 10, 0, 10, 10};
        Band band = ProjectionProfile.densestBand(counts, 2, 5).orElseThrow();
        assertEquals(0, band.start());
        assertEquals(5, band.endExclusive());
    }

    @Test
    void wideGapBlocksTheBand() {
        // A wide gap (>= blockingGap) is the running-title gutter and must not be bridged; the
        // denser run on the left wins.
        int[] counts = {10, 10, 0, 0, 0, 0, 5, 5};
        Band band = ProjectionProfile.densestBand(counts, 10, 3).orElseThrow();
        assertEquals(0, band.start());
        assertEquals(2, band.endExclusive());
    }

    @Test
    void emptyHistogramHasNoBand() {
        int[] counts = {0, 0, 0};
        assertTrue(ProjectionProfile.densestBand(counts, 1, 1).isEmpty());
    }

    @Test
    void inkBoundsSpansAcrossGaps() {
        // Two inked runs with a wide blank between: the bbox spans both, never fragmenting.
        int[] counts = {0, 10, 0, 0, 0, 10, 0};
        Band band = ProjectionProfile.inkBounds(counts).orElseThrow();
        assertEquals(1, band.start());
        assertEquals(6, band.endExclusive());
    }

    @Test
    void inkBoundsExcludesSubThresholdNoise() {
        // 5 is below max/8 (=10) and ignored; the bbox is just the two dense positions and between.
        int[] counts = {5, 80, 0, 80, 5};
        Band band = ProjectionProfile.inkBounds(counts).orElseThrow();
        assertEquals(1, band.start());
        assertEquals(4, band.endExclusive());
    }

    @Test
    void inkBoundsEmptyWhenNoInk() {
        assertTrue(ProjectionProfile.inkBounds(new int[] {0, 0, 0}).isEmpty());
    }
}
