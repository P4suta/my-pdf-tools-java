package io.github.p4suta.webapp.domain;

import io.github.p4suta.shared.kernel.Validators;

/**
 * A job's opaque, unguessable identity — a UUID string at runtime, so a result URL cannot be
 * guessed from another's. Restricted to a filesystem- and URL-safe token ({@code [A-Za-z0-9_-]+})
 * so an id taken from a request path can never escape its workspace directory (path traversal).
 *
 * @param value the identity string
 */
public record JobId(String value) {

    /** Validates the id. */
    public JobId {
        Validators.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("job id must not be blank");
        }
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("job id must be a safe token: " + value);
        }
    }
}
