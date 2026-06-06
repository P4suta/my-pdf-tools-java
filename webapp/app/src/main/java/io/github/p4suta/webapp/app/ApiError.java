package io.github.p4suta.webapp.app;

import org.jspecify.annotations.Nullable;

/**
 * A uniform error response body.
 *
 * @param error a short, stable error code
 * @param message a human-readable description, if any
 */
public record ApiError(String error, @Nullable String message) {}
