package io.github.p4suta.shared.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class ValidatorsTest {

    // --- requirePositive(int) ---

    @Test
    void requirePositiveIntReturnsAPositiveValue() {
        assertEquals(1, Validators.requirePositive(1, "dpi"));
        assertEquals(300, Validators.requirePositive(300, "dpi"));
    }

    @Test
    void requirePositiveIntRejectsZeroAndNegativeWithDespeckleStyleMessage() {
        IllegalArgumentException zero =
                assertThrows(
                        IllegalArgumentException.class, () -> Validators.requirePositive(0, "dpi"));
        assertEquals("dpi must be positive: 0", zero.getMessage());

        IllegalArgumentException negative =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Validators.requirePositive(-5, "speckSizePx"));
        assertEquals("speckSizePx must be positive: -5", negative.getMessage());
    }

    // --- requireNonNegative(int) ---

    @Test
    void requireNonNegativeReturnsZeroAndPositive() {
        assertEquals(0, Validators.requireNonNegative(0, "count"));
        assertEquals(7, Validators.requireNonNegative(7, "count"));
    }

    @Test
    void requireNonNegativeRejectsNegative() {
        IllegalArgumentException negative =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Validators.requireNonNegative(-1, "count"));
        assertEquals("count must be non-negative: -1", negative.getMessage());
    }

    // --- requirePositive(double) ---

    @Test
    void requirePositiveDoubleReturnsAPositiveValue() {
        assertEquals(0.5, Validators.requirePositive(0.5, "mm"));
    }

    @Test
    void requirePositiveDoubleRejectsZeroNegativeAndNaN() {
        IllegalArgumentException zero =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Validators.requirePositive(0.0, "mm"));
        assertEquals("mm must be positive: 0.0", zero.getMessage());

        IllegalArgumentException negative =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Validators.requirePositive(-2.5, "mm"));
        assertEquals("mm must be positive: -2.5", negative.getMessage());

        // !(value > 0) also rejects NaN.
        IllegalArgumentException nan =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Validators.requirePositive(Double.NaN, "mm"));
        assertEquals("mm must be positive: NaN", nan.getMessage());
    }

    // --- requireNonNull ---

    @Test
    void requireNonNullReturnsTheSameInstance() {
        String value = "x";
        assertSame(value, Validators.requireNonNull(value, "value"));
    }

    @Test
    void requireNonNullRejectsNull() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Validators.requireNonNull(null, "paper"));
        assertEquals("paper must not be null", ex.getMessage());
    }

    // --- the utility class itself ---

    @Test
    void isANonInstantiableUtilityClass() throws ReflectiveOperationException {
        Constructor<Validators> ctor = Validators.class.getDeclaredConstructor();
        assertEquals(0, Modifier.PUBLIC & ctor.getModifiers() & Modifier.PROTECTED);
        ctor.setAccessible(true);
        // Invoking it for coverage; it does nothing.
        ctor.newInstance();
    }
}
