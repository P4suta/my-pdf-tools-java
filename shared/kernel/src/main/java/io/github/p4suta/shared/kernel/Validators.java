package io.github.p4suta.shared.kernel;

import org.jspecify.annotations.Nullable;

/**
 * Exception-neutral precondition checks. Each throws a bare {@link IllegalArgumentException} with a
 * {@code "<name> must be ...: <value>"} message and returns its argument for inline use. Callers
 * needing a richer tagged error (e.g. tate-yoko-pdf's {@code SpreadException}) wrap these, so this
 * module depends on no app's error model.
 */
public final class Validators {

    private Validators() {}

    /** Requires {@code value > 0}. */
    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    /** Requires {@code value >= 0}. */
    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }

    /** Requires {@code value > 0}; the {@code !(value > 0)} test also rejects {@code NaN}. */
    public static double requirePositive(double value, String name) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    /** Requires {@code value} to be non-null. */
    public static <T> T requireNonNull(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
