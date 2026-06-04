package io.github.p4suta.register.domain.model;

/**
 * A page's skew reading, captured during a diagnostics run: the angle the skew finder measured, its
 * confidence, whether an estimate was produced at all, and whether the page was straightened
 * (whether the estimate cleared the confidence and angle thresholds). A pure value type — the
 * Leptonica skew finder that produces it lives in {@code :infrastructure}.
 *
 * @param angleDeg estimated tilt in degrees (pixFindSkew convention)
 * @param conf confidence ratio
 * @param found whether an estimate was produced at all
 * @param applied whether the page was actually straightened (passed the thresholds)
 */
public record Skew(double angleDeg, double conf, boolean found, boolean applied) {}
