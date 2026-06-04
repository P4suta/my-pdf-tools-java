package io.github.p4suta.shared.kernel;

import org.jspecify.annotations.Nullable;

/**
 * Exception-neutral precondition checks shared across the apps. Each throws a bare {@link
 * IllegalArgumentException} with a message in the form {@code "<name> must be ...: <value>"} — the
 * same vocabulary the register and despeckle domains hand-roll today, so their existing tests keep
 * passing after they migrate to these. Each check returns its argument so it can be used inline.
 *
 * <p>This is the neutral layer: callers that need a richer, tagged error (for example
 * tate-yoko-pdf's {@code SpreadException} + {@code ErrorKind}) wrap these or layer their own
 * variants on top rather than have this module depend on any app's error model.
 */
public final class Validators {

    private Validators() {}

    /**
     * Requires {@code value > 0}.
     *
     * @param value the value to check
     * @param name the value's name, used in the failure message
     * @return {@code value}, when positive
     * @throws IllegalArgumentException if {@code value <= 0}
     */
    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    /**
     * Requires {@code value >= 0}.
     *
     * @param value the value to check
     * @param name the value's name, used in the failure message
     * @return {@code value}, when non-negative
     * @throws IllegalArgumentException if {@code value < 0}
     */
    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
        return value;
    }

    /**
     * Requires {@code value > 0}. The {@code !(value > 0)} test also rejects {@code NaN}.
     *
     * @param value the value to check
     * @param name the value's name, used in the failure message
     * @return {@code value}, when positive
     * @throws IllegalArgumentException if {@code value} is not greater than zero (including NaN)
     */
    public static double requirePositive(double value, String name) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    /**
     * Requires {@code value} to be non-null.
     *
     * @param value the value to check
     * @param name the value's name, used in the failure message
     * @param <T> the value's type
     * @return {@code value}, when non-null
     * @throws IllegalArgumentException if {@code value} is null
     */
    public static <T> T requireNonNull(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
