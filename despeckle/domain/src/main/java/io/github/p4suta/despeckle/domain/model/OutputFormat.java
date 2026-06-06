package io.github.p4suta.despeckle.domain.model;

import org.jspecify.annotations.Nullable;

/** Selects the on-disk format for cleaned pages. */
public enum OutputFormat {
    /** Keep the input file's format (and extension). */
    SAME,
    /** Write every page as binary PBM (P4). */
    PBM,
    /** Write every page as PNG. */
    PNG,
    /** Write every page as CCITT Group-4 TIFF (1 bpp, lossless) — the pipeline's intermediate. */
    TIFF,
    /** Write every page as lossless WebP. */
    WEBP;

    /** The output file extension, or {@code null} to keep the input's. */
    public @Nullable String extension() {
        return switch (this) {
            case SAME -> null;
            case PBM -> "pbm";
            case PNG -> "png";
            // ".tif" (not ".tiff") is deliberate: the unified pipeline tells despeckle's stage
            // output apart from register's by extension, so this MUST stay distinct from register's
            // OutputFormat.TIFF -> "tiff". Unifying the two enums would silently break the
            // pipeline.
            case TIFF -> "tif";
            case WEBP -> "webp";
        };
    }
}
