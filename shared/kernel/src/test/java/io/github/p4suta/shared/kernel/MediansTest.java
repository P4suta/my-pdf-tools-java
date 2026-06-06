package io.github.p4suta.shared.kernel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class MediansTest {

    // upperMedian(int[])

    @Test
    void upperMedianIntOddCountIsTheSingleMiddle() {
        // sorted {1,2,3} -> index 3/2 = 1 -> 2.
        assertEquals(2, Medians.upperMedian(new int[] {3, 1, 2}));
    }

    @Test
    void upperMedianIntEvenCountIsTheUpperOfTheTwoMiddles() {
        // sorted {1,2,3,4} -> index 4/2 = 2 -> 3 (the UPPER middle, not the lower 2 or an average).
        // This locks the even-count tie-break against a regression to (n-1)/2 or an averaging
        // median.
        assertEquals(3, Medians.upperMedian(new int[] {4, 2, 1, 3}));
    }

    @Test
    void upperMedianIntSingleElement() {
        assertEquals(42, Medians.upperMedian(new int[] {42}));
    }

    @Test
    void upperMedianIntDoesNotMutateTheInput() {
        // Unsorted input: if clone() were dropped, Arrays.sort would reorder this in place. An
        // already-sorted input would pass even without the clone, so it must be unsorted.
        int[] input = {3, 1, 2};
        Medians.upperMedian(input);
        assertArrayEquals(new int[] {3, 1, 2}, input);
    }

    // upperMedian(long[])

    @Test
    void upperMedianLongOddCountIsTheSingleMiddle() {
        assertEquals(2L, Medians.upperMedian(new long[] {3L, 1L, 2L}));
    }

    @Test
    void upperMedianLongEvenCountIsTheUpperOfTheTwoMiddles() {
        // sorted {1,2,3,4} -> index 2 -> 3, the UPPER middle. Same contract as the int overload.
        assertEquals(3L, Medians.upperMedian(new long[] {4L, 2L, 1L, 3L}));
    }

    @Test
    void upperMedianLongSingleElement() {
        assertEquals(42L, Medians.upperMedian(new long[] {42L}));
    }

    @Test
    void upperMedianLongDoesNotMutateTheInput() {
        long[] input = {3L, 1L, 2L};
        Medians.upperMedian(input);
        assertArrayEquals(new long[] {3L, 1L, 2L}, input);
    }

    // the utility class itself

    @Test
    void isANonInstantiableUtilityClass() throws ReflectiveOperationException {
        Constructor<Medians> ctor = Medians.class.getDeclaredConstructor();
        assertEquals(0, Modifier.PUBLIC & ctor.getModifiers() & Modifier.PROTECTED);
        ctor.setAccessible(true);
        // Invoking it for coverage; it does nothing.
        ctor.newInstance();
    }
}
