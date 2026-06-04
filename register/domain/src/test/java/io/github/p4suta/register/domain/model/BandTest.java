package io.github.p4suta.register.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The half-open band's range invariant ({@code endExclusive > start}) and its length. */
class BandTest {

    @Test
    void rejectsAnEmptyOrInvertedRange() {
        // endExclusive == start (empty) and endExclusive < start (inverted) are both rejected.
        assertThrows(IllegalArgumentException.class, () -> new Band(5, 5));
        assertThrows(IllegalArgumentException.class, () -> new Band(5, 3));
    }

    @Test
    void lengthIsTheNumberOfIndices() {
        assertEquals(3, new Band(2, 5).length());
        assertEquals(1, new Band(0, 1).length());
    }
}
