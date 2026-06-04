package io.github.p4suta.register.domain.model;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One page's analysis-pass result, in Pix-free terms so it can cross the {@code :port} boundary:
 * the deskewed page's size, its detected main column (if any), and — only on a diagnostics run —
 * the skew that was measured. The corpus reference is derived from the detections across all pages;
 * the render pass places each page using its own analysis.
 *
 * <p>{@code sourceFormat} is an opaque token the registrar adapter produces in the analysis pass
 * and reads back in the render pass to reproduce the input's on-disk format for {@code --format
 * same} (it is the Leptonica {@code IFF_*} code, but the domain never interprets it — it only
 * stores and returns it, so no Leptonica type or constant leaks into this layer). The deskewed page
 * is written to a scratch file in a fixed lossless format, so the original format cannot be
 * recovered from it; round-tripping this token through the analysis keeps the adapter stateless and
 * thread-safe.
 *
 * @param width the deskewed page width in pixels
 * @param height the deskewed page height in pixels
 * @param detection the detected main column, or empty when none was found
 * @param skew the measured skew, or null when not recording diagnostics
 * @param sourceFormat the registrar adapter's opaque source-format token (see above)
 */
public record PageAnalysis(
        int width,
        int height,
        Optional<Detection> detection,
        @Nullable Skew skew,
        int sourceFormat) {}
