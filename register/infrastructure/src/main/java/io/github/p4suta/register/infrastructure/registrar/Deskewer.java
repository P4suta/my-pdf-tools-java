package io.github.p4suta.register.infrastructure.registrar;

import io.github.p4suta.shared.imaging.Pix;

/**
 * The register-specific deskew policy, layered on the shared {@link Pix} primitives (the shared
 * imaging island exposes only raw rotations and the raw {@link Pix#findSkew() skew estimate}, no
 * project policy). Owns the threshold constants that decide when a measured tilt is corrected.
 *
 * <p>The page is first rotated 90 degrees so the vertical text columns become the horizontal rows
 * Leptonica's row-projection skew finder expects, the skew is measured there, and — only if it is
 * reliable ({@link #MIN_DESKEW_CONFIDENCE}) and a real tilt (between {@link
 * #MIN_DESKEW_ANGLE_DEGREES} and {@link #MAX_DESKEW_ANGLE_DEGREES}) — the page is straightened
 * before being rotated back. A straight page (skew below the floor) is passed through untouched, so
 * a page that is already square is never given an invented tilt.
 *
 * <p>We measure and rotate ourselves rather than calling Leptonica's {@code pixDeskew}, whose
 * built-in confidence gate (3.0) rejects the marginal-but-accurate estimates this vertical-text
 * corpus produces (confidence ~2.1–2.4) and so would leave every page un-straightened.
 */
public final class Deskewer {

    /** Below this confidence ratio, the skew estimate is treated as noise — no rotation. */
    static final double MIN_DESKEW_CONFIDENCE = 1.5;

    /** Skew below this (degrees) is within scan noise: left alone, so straight pages stay put. */
    static final double MIN_DESKEW_ANGLE_DEGREES = 0.3;

    /** Skew beyond this (degrees) is treated as a misdetection, not a real tilt: left alone. */
    static final double MAX_DESKEW_ANGLE_DEGREES = 8.0;

    private Deskewer() {}

    /**
     * Deskew {@code page}, returning a fresh caller-owned {@link Pix}: rotate 90 degrees, measure
     * the skew, and — only if the estimate is {@linkplain SkewEstimate#correctable() correctable} —
     * rotate it straight before rotating back; otherwise just complete the 90-degree round trip.
     *
     * @param page the page to straighten (not modified; not closed)
     */
    public static Pix deskew(Pix page) {
        return deskewWithEstimate(page).page();
    }

    /**
     * Deskew {@code page} and return the single skew estimate that drove the decision, so a caller
     * that also wants the diagnostic reading need not re-run {@link #measureSkew(Pix)} (a second
     * 90-degree rotation and {@code pixFindSkew}). The page is measured once: that one estimate
     * both gates the straightening (via {@link SkewEstimate#correctable()}) and is the {@code Skew}
     * diagnostic, so the rotation decision and the recorded reading cannot drift. Caller-owned
     * page.
     *
     * @param page the page to straighten (not modified; not closed)
     */
    public static DeskewResult deskewWithEstimate(Pix page) {
        try (Pix rotated = page.rotateOrth(1)) {
            SkewEstimate est = measureRotated(rotated);
            if (est.correctable()) {
                try (Pix straight = rotated.rotate(Math.toRadians(est.angleDeg()))) {
                    return new DeskewResult(straight.rotateOrth(3), est);
                }
            }
            return new DeskewResult(rotated.rotateOrth(3), est);
        }
    }

    /**
     * A deskew outcome: the straightened {@code page} (caller-owned) paired with the single {@code
     * estimate} that {@link #deskewWithEstimate(Pix)} measured and gated the straightening on.
     */
    public record DeskewResult(Pix page, SkewEstimate estimate) {}

    /**
     * Estimate {@code page}'s skew the way {@link #deskew(Pix)} sees it: rotate 90 degrees so the
     * vertical columns become horizontal rows (what Leptonica's row-projection finder expects),
     * then take the raw shared skew estimate. Its {@link SkewEstimate#correctable()} is the same
     * predicate {@code deskew} gates straightening on.
     *
     * @param page the page to measure (not modified; not closed)
     */
    public static SkewEstimate measureSkew(Pix page) {
        try (Pix rotated = page.rotateOrth(1)) {
            return measureRotated(rotated);
        }
    }

    /** Take the raw shared skew estimate of an already-horizontalized page. */
    private static SkewEstimate measureRotated(Pix horizontalText) {
        Pix.SkewEstimate raw = horizontalText.findSkew();
        return new SkewEstimate(raw.angleDeg(), raw.conf(), raw.found());
    }

    /**
     * A register skew reading (angle in degrees, confidence ratio, and whether Leptonica produced
     * an estimate), adding the deskew policy's {@link #correctable()} gate to the raw shared
     * estimate.
     */
    public record SkewEstimate(double angleDeg, double conf, boolean found) {

        /**
         * Whether {@link Deskewer#deskew(Pix)} would straighten a page carrying this estimate: a
         * reliable (confidence at least {@link #MIN_DESKEW_CONFIDENCE}) and real tilt (between
         * {@link #MIN_DESKEW_ANGLE_DEGREES} and {@link #MAX_DESKEW_ANGLE_DEGREES} degrees).
         */
        public boolean correctable() {
            return found
                    && conf >= MIN_DESKEW_CONFIDENCE
                    && Math.abs(angleDeg) >= MIN_DESKEW_ANGLE_DEGREES
                    && Math.abs(angleDeg) <= MAX_DESKEW_ANGLE_DEGREES;
        }
    }
}
