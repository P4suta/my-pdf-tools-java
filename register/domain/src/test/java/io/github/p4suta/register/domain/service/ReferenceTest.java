package io.github.p4suta.register.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageObservation;
import io.github.p4suta.register.domain.model.Parity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceTest {

    @Test
    void takesPerParityComponentMedianAndDropsOutliers() {
        List<PageObservation> observations =
                List.of(
                        new PageObservation(0, Parity.RECTO, new Box(10, 10, 100, 200)),
                        new PageObservation(2, Parity.RECTO, new Box(20, 10, 100, 200)),
                        new PageObservation(4, Parity.RECTO, new Box(30, 10, 100, 200)),
                        // A tiny detection failure: far below half the median area, so excluded.
                        new PageObservation(6, Parity.RECTO, new Box(1, 1, 5, 5)),
                        new PageObservation(1, Parity.VERSO, new Box(50, 10, 100, 200)),
                        new PageObservation(3, Parity.VERSO, new Box(60, 10, 100, 200)),
                        new PageObservation(5, Parity.VERSO, new Box(70, 10, 100, 200)));

        Reference reference = Reference.fromObservations(observations, 0.5);

        assertEquals(new Box(20, 10, 100, 200), reference.forParity(Parity.RECTO));
        assertEquals(new Box(60, 10, 100, 200), reference.forParity(Parity.VERSO));
    }

    @Test
    void rejectsAnEmptyCorpus() {
        // No analyzed page means no median can be taken — the run has nothing to register onto.
        assertThrows(
                IllegalArgumentException.class, () -> Reference.fromObservations(List.of(), 0.5));
    }

    @Test
    void aSingleParityCorpusReusesTheWholeSetForTheMissingParity() {
        // A very short run with only recto pages: verso has no boxes of its own, so its reference
        // falls back to the whole corpus (the pool.isEmpty() branch in medianForParity). With only
        // recto pages present, that whole-corpus pool IS the recto pool, so both parities resolve
        // to
        // the same reference box.
        List<PageObservation> rectoOnly =
                List.of(
                        new PageObservation(0, Parity.RECTO, new Box(10, 10, 100, 200)),
                        new PageObservation(2, Parity.RECTO, new Box(20, 10, 100, 200)),
                        new PageObservation(4, Parity.RECTO, new Box(30, 10, 100, 200)));

        Reference reference = Reference.fromObservations(rectoOnly, 0.5);

        assertEquals(new Box(20, 10, 100, 200), reference.forParity(Parity.RECTO));
        assertEquals(reference.forParity(Parity.RECTO), reference.forParity(Parity.VERSO));
    }
}
