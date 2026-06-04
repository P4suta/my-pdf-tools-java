package io.github.p4suta.register.infrastructure.registrar;

import io.github.p4suta.shared.imaging.Pix;

/**
 * The register-specific deskew POLICY, layered on the shared {@link Pix} PRIMITIVES.
 *
 * <p>The shared imaging island exposes only raw pixel operations — the orthogonal and arbitrary
 * rotations and the RAW {@link Pix#findSkew() skew estimate} — and deliberately keeps no project
 * policy. This class is the register side of that split: it reproduces, bit-for-bit, the
 * confidence-gated deskew the old register-local {@code Pix.deskew()} performed, and owns the
 * threshold constants that decide when a measured tilt is corrected.
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
     * Deskew {@code page}, returning a fresh {@link Pix}. Reproduces the old register {@code
     * Pix.deskew()} exactly: rotate 90 degrees, measure the skew, and — only if the estimate is
     * {@linkplain SkewEstimate#correctable() correctable} — rotate it straight before rotating
     * back; otherwise just complete the 90-degree round trip. The caller owns the returned {@code
     * Pix}.
     *
     * @param page the page to straighten (not modified; not closed)
     * @return a new, owned {@code Pix} holding the deskewed page
     */
    public static Pix deskew(Pix page) {
        return deskewWithEstimate(page).page();
    }

    /**
     * Deskew {@code page} AND return the single skew estimate that drove the decision, so a caller
     * that also wants the diagnostic reading does not have to re-run {@link #measureSkew(Pix)}
     * (which would repeat the same native 90-degree rotation and {@code pixFindSkew}). The page is
     * rotated 90 degrees and measured ONCE: that one estimate both gates the straightening (via
     * {@link SkewEstimate#correctable()}) and is handed back for the {@code Skew} diagnostic, so
     * the rotation decision and the recorded reading literally come from the same measurement and
     * cannot drift. The caller owns the returned {@code Pix}.
     *
     * @param page the page to straighten (not modified; not closed)
     * @return the deskewed page and the one skew estimate it was measured against
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
     * A deskew outcome: the {@code page} straightened (the caller owns it) paired with the single
     * {@code estimate} that {@link #deskewWithEstimate(Pix)} measured and gated the straightening
     * on.
     *
     * @param page the deskewed page; caller-owned
     * @param estimate the one skew reading that drove the deskew decision
     */
    public record DeskewResult(Pix page, SkewEstimate estimate) {}

    /**
     * Estimate {@code page}'s skew the way {@link #deskew(Pix)} sees it: rotate 90 degrees so the
     * vertical columns become horizontal rows (what Leptonica's row-projection finder expects),
     * then take the RAW shared skew estimate. The returned estimate's {@link
     * SkewEstimate#correctable()} is the same predicate {@code deskew} gates the straightening on,
     * so a diagnostic {@code Skew} and the actual rotation decision are driven by one measurement
     * and cannot drift.
     *
     * @param page the page to measure (not modified; not closed)
     * @return the skew estimate, with the register deskew policy's correctable predicate
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
     * A register skew reading: {@code angleDeg} degrees, {@code conf} the confidence ratio, {@code
     * found} whether Leptonica produced an estimate. Adds the register deskew policy's {@link
     * #correctable()} gate to the raw shared estimate.
     *
     * @param angleDeg the estimated skew angle in degrees
     * @param conf the confidence ratio Leptonica assigns the estimate
     * @param found whether an estimate was produced
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
