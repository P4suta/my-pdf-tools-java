package io.github.p4suta.register.domain.model;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One page's analysis-pass result, in Pix-free terms so it can cross the {@code :port} boundary:
 * the deskewed page's size, its detected main column, and — only on a diagnostics run — the
 * measured skew. The corpus reference is derived from the detections across all pages; the render
 * pass places each page using its own analysis.
 *
 * <p>{@code sourceFormat} is an opaque token the registrar adapter produces in the analysis pass
 * and reads back in the render pass to reproduce the input's on-disk format for {@code --format
 * same}. It is the Leptonica {@code IFF_*} code, but the domain only stores and returns it, so no
 * Leptonica type leaks into this layer. The deskewed page is written to a scratch file in a fixed
 * lossless format, so the original format cannot be recovered from it; round-tripping the token
 * keeps the adapter stateless and thread-safe.
 *
 * @param detection the detected main column, or empty when none was found
 * @param skew the measured skew, or null when not recording diagnostics
 */
public record PageAnalysis(
        int width,
        int height,
        Optional<Detection> detection,
        @Nullable Skew skew,
        int sourceFormat) {}
