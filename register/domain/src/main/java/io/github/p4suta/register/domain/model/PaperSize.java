package io.github.p4suta.register.domain.model;

import io.github.p4suta.shared.kernel.Resolution;
import io.github.p4suta.shared.kernel.Validators;
import java.util.Locale;

/** A target paper size in millimeters, convertible to a pixel canvas at a given resolution. */
public sealed interface PaperSize permits PaperSize.Standard, PaperSize.Custom {

    /**
     * Largest average per-axis deviation at which a scanned size is still snapped to a standard
     * book size. A bound book's scanned pages miss the nominal size by a few percent — trimming at
     * binding, paper shrink/expansion, scan error — so a tolerant nearest-match (not "smallest
     * enclosing standard") is what recognizes a trimmed 四六判 as 四六判 instead of overshooting to the
     * next size up.
     */
    double AUTO_SNAP_TOLERANCE = 0.08;

    /**
     * How much an oversized scan (a page measured larger than a standard) counts against matching
     * that standard, relative to an undersized one. Loose cropping and scan margin only ADD to a
     * page's measured size, and the registration crops that overflow anyway, so measuring larger
     * than a standard is weak evidence against it — whereas measuring smaller would mean cropping
     * real content. Halving the oversize penalty makes a loosely-cut 文庫 snap back to A6 instead of
     * jumping up to B6.
     */
    double OVERSIZE_PENALTY = 0.5;

    /** Width in millimeters. */
    double widthMm();

    /** Height in millimeters. */
    double heightMm();

    /** Width in pixels at {@code dpi}. */
    default int widthPx(int dpi) {
        return Resolution.of(dpi).pxFromMm(widthMm());
    }

    /** Height in pixels at {@code dpi}. */
    default int heightPx(int dpi) {
        return Resolution.of(dpi).pxFromMm(heightMm());
    }

    /** A short human-readable name for logs and the run summary. */
    default String displayName() {
        return switch (this) {
            case Standard s -> s.name().toLowerCase(Locale.ROOT);
            case Custom c ->
                    String.format(Locale.ROOT, "custom %.1fx%.1f mm", c.widthMm(), c.heightMm());
        };
    }

    /** The common Japanese book and ISO/JIS sizes. */
    enum Standard implements PaperSize {
        /** 四六判 (default). */
        SHIROKU(127, 188),
        A4(210, 297),
        A5(148, 210),
        /** 文庫判. */
        A6(105, 148),
        B5(182, 257),
        B6(128, 182),
        /** 新書判. */
        SHINSHO(103, 182);

        private final double widthMm;
        private final double heightMm;

        Standard(double widthMm, double heightMm) {
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }

        @Override
        public double widthMm() {
            return widthMm;
        }

        @Override
        public double heightMm() {
            return heightMm;
        }
    }

    /** An arbitrary size in millimeters, e.g. from {@code --paper-mm 127x188}. */
    record Custom(double widthMm, double heightMm) implements PaperSize {

        /** Validates the dimensions. */
        public Custom {
            Validators.requirePositive(widthMm, "widthMm");
            Validators.requirePositive(heightMm, "heightMm");
        }
    }

    /** Parse a standard name ({@code "a4"}, {@code "shiroku"}) or a {@code "WxH"} mm spec. */
    static PaperSize parse(String spec) {
        String s = spec.trim();
        int sep = s.indexOf('x');
        if (sep < 0) {
            sep = s.indexOf('X');
        }
        try {
            if (sep > 0) {
                double width = Double.parseDouble(s.substring(0, sep).trim());
                double height = Double.parseDouble(s.substring(sep + 1).trim());
                return new Custom(width, height);
            }
            return Standard.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // NumberFormatException (bad WxH) and enum valueOf both extend
            // IllegalArgumentException.
            throw new IllegalArgumentException(
                    "unrecognized paper size '"
                            + spec
                            + "'; use a standard name (shiroku, a4, a5, a6, b5, b6, shinsho) or a"
                            + " custom WxH in millimeters (e.g. 127x188)",
                    e);
        }
    }

    /**
     * The paper for a scanned book whose median page measures {@code widthMm x heightMm}: the
     * nearest {@link Standard} when one is within {@link #AUTO_SNAP_TOLERANCE} (snapped to its
     * clean nominal dimensions, so a trimmed, shrunk or loosely-cropped book still lands on its
     * true size), otherwise the exact measured size as a {@link Custom}.
     *
     * <p>Matched by average per-axis deviation, asymmetric by direction (see {@link
     * #OVERSIZE_PENALTY}): a page measured <em>smaller</em> than a standard counts in full (going
     * smaller would crop real content), while a page measured <em>larger</em> counts at a discount
     * (the excess is loose-cut margin the registration crops anyway). So a 文庫 trimmed a few mm
     * under nominal still resolves to A6, and one scanned with a loose margin a few mm over nominal
     * snaps back down to A6 rather than jumping up to B6.
     */
    static PaperSize fromScan(double widthMm, double heightMm) {
        Standard nearest = Standard.A6;
        double nearestDeviation = Double.MAX_VALUE;
        for (Standard candidate : Standard.values()) {
            double deviation =
                    (axisDeviation(widthMm, candidate.widthMm())
                                    + axisDeviation(heightMm, candidate.heightMm()))
                            / 2.0;
            if (deviation < nearestDeviation) {
                nearestDeviation = deviation;
                nearest = candidate;
            }
        }
        if (nearestDeviation <= AUTO_SNAP_TOLERANCE) {
            return nearest;
        }
        return new Custom(roundTenth(widthMm), roundTenth(heightMm));
    }

    private static double axisDeviation(double measured, double nominal) {
        double relative = Math.abs(measured - nominal) / nominal;
        return measured > nominal ? relative * OVERSIZE_PENALTY : relative;
    }

    private static double roundTenth(double mm) {
        return Math.round(mm * 10.0) / 10.0;
    }
}
