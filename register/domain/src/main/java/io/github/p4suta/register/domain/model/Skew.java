package io.github.p4suta.register.domain.model;

/**
 * A page's skew reading, captured during a diagnostics run. The Leptonica skew finder that produces
 * it lives in {@code :infrastructure}.
 *
 * @param angleDeg estimated tilt in degrees (pixFindSkew convention)
 * @param found whether an estimate was produced at all
 * @param applied whether the page was straightened (the estimate cleared the confidence and angle
 *     thresholds)
 */
public record Skew(double angleDeg, double conf, boolean found, boolean applied) {}
