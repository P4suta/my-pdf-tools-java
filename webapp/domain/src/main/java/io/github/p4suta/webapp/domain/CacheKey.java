package io.github.p4suta.webapp.domain;

import io.github.p4suta.shared.kernel.Validators;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A content-addressed key identifying a conversion's output: the SHA-256 of the input PDF combined
 * with the options that affect the produced book. Two submissions with the same input bytes and the
 * same options share a key, and therefore a cached result. The worker-thread count ({@link
 * ConversionRequest#jobs}) is excluded: it changes only how fast pdfbook runs, not what it
 * produces, so a {@code -j 4} and a {@code -j 8} run reuse the same cache entry.
 *
 * @param value a lowercase-hex SHA-256 token, safe to use directly as a directory name
 */
public record CacheKey(String value) {

    public CacheKey {
        Validators.requireNonNull(value, "value");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("cache key must be a sha-256 hex token: " + value);
        }
    }

    /**
     * {@return the key for an input identified by {@code inputSha256} converted with {@code
     * request}}
     *
     * @param inputSha256 the lowercase-hex SHA-256 of the uploaded PDF bytes
     * @param request the conversion options (its worker count is ignored)
     */
    public static CacheKey of(String inputSha256, ConversionRequest request) {
        Validators.requireNonNull(inputSha256, "inputSha256");
        Validators.requireNonNull(request, "request");
        // Canonical encoding of everything that affects the output, hashed to a fixed token. Field
        // order and names are stable; `jobs` is omitted (see class doc).
        String canonical =
                String.join(
                        "\n",
                        "sha256:" + inputSha256,
                        "direction:" + request.direction().name(),
                        "firstPage:" + request.firstPage().name(),
                        "despeckle:" + request.despeckle(),
                        "register:" + request.register(),
                        "deskew:" + request.deskew(),
                        "scale:" + request.scale(),
                        "pdfA:" + request.pdfA());
        return new CacheKey(sha256Hex(canonical));
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required algorithm on every conformant JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
