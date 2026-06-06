package io.github.p4suta.shared.kernel;

/**
 * A scan resolution: a positive {@code dpi} (dots per inch) that converts between physical
 * millimeters/inches and pixels.
 *
 * @param dpi the resolution in dots per inch; must be positive
 */
public record Resolution(int dpi) {

    private static final double MM_PER_INCH = 25.4;

    public Resolution {
        Validators.requirePositive(dpi, "dpi");
    }

    public static Resolution of(int dpi) {
        return new Resolution(dpi);
    }

    /** Pixels in {@code mm} millimeters, rounded half-up: {@code Math.round(mm * dpi / 25.4)}. */
    public int pxFromMm(double mm) {
        return (int) Math.round(mm * dpi / MM_PER_INCH);
    }

    /** Pixels in {@code inches} inches, rounded half-up: {@code Math.round(inches * dpi)}. */
    public int pxFromInch(double inches) {
        return (int) Math.round(inches * dpi);
    }

    /**
     * Millimeters in {@code px} pixels: {@code px * 25.4 / dpi}, the inverse of {@link #pxFromMm}.
     */
    public double mmFromPx(int px) {
        return px * MM_PER_INCH / dpi;
    }
}
