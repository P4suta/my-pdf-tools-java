package io.github.p4suta.register.domain.model;

import org.jspecify.annotations.Nullable;

/** Selects the on-disk format for registered pages. */
public enum OutputFormat {
    /** Keep the input file's format (and extension). */
    SAME,
    /** Write every page as binary PBM (P4). */
    PBM,
    /** Write every page as PNG. */
    PNG,
    /** Write every page as CCITT Group-4 bitonal TIFF. */
    TIFF,
    /** Write every page as lossless WebP. */
    WEBP;

    // The Leptonica IFF_* mapping lives with the binding in :infrastructure (the page registrar
    // calls Pix's named write wrappers per this enum), so this domain enum carries no Leptonica
    // coupling.

    /** The output file extension, or {@code null} to keep the input's. */
    public @Nullable String extension() {
        return switch (this) {
            case SAME -> null;
            case PBM -> "pbm";
            case PNG -> "png";
            // ".tiff" (not ".tif") is deliberate: the unified pipeline tells register's stage
            // output
            // apart from despeckle's by extension, so this MUST stay distinct from despeckle's
            // OutputFormat.TIFF -> "tif". Unifying the two enums would silently break the pipeline.
            case TIFF -> "tiff";
            case WEBP -> "webp";
        };
    }
}
