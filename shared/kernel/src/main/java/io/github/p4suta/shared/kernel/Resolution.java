package io.github.p4suta.shared.kernel;

/**
 * A scan resolution: a positive {@code dpi} (dots per inch) that converts between physical
 * millimeters/inches and pixels. The conversion replicates, bit-for-bit, the rounding the register
 * domain hand-rolls in {@code PaperSize.widthPx}/{@code heightPx}: {@code (int) Math.round(mm * dpi
 * / 25.4)}, with the same operand order so the result is identical when register migrates to this
 * type. 25.4 is millimeters per inch.
 *
 * @param dpi the resolution in dots per inch; must be positive
 */
public record Resolution(int dpi) {

    /** Millimeters per inch — the constant register divides by in its mm-to-px conversion. */
    private static final double MM_PER_INCH = 25.4;

    /** Validates that {@code dpi} is positive. */
    public Resolution {
        Validators.requirePositive(dpi, "dpi");
    }

    /**
     * The resolution at {@code dpi} dots per inch.
     *
     * @param dpi the resolution; must be positive
     * @return the resolution
     * @throws IllegalArgumentException if {@code dpi <= 0}
     */
    public static Resolution of(int dpi) {
        return new Resolution(dpi);
    }

    /**
     * The pixel width of {@code mm} millimeters at this resolution, rounded exactly as register's
     * {@code PaperSize.widthPx}: {@code (int) Math.round(mm * dpi / 25.4)} (half-up to the nearest
     * pixel). The operand order {@code mm * dpi / 25.4} is preserved so the floating-point result
     * matches register's bit-for-bit.
     *
     * @param mm a length in millimeters
     * @return the equivalent length in pixels
     */
    public int pxFromMm(double mm) {
        return (int) Math.round(mm * dpi / MM_PER_INCH);
    }

    /**
     * The pixel width of {@code inches} inches at this resolution, rounded half-up to the nearest
     * pixel: {@code (int) Math.round(inches * dpi)}.
     *
     * @param inches a length in inches
     * @return the equivalent length in pixels
     */
    public int pxFromInch(double inches) {
        return (int) Math.round(inches * dpi);
    }

    /**
     * The physical length in millimeters of {@code px} pixels at this resolution: {@code px * 25.4
     * / dpi}. The exact inverse of {@link #pxFromMm(double)} before rounding.
     *
     * @param px a length in pixels
     * @return the equivalent length in millimeters
     */
    public double mmFromPx(int px) {
        return px * MM_PER_INCH / dpi;
    }
}
