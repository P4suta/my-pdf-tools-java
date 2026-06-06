package io.github.p4suta.register.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * Whole-run diagnostic context: the run's settings and the derived per-parity references. The
 * per-page {@link PageDiagnostic}s are held by the reporter adapter itself, so the report combines
 * this with that list rather than carrying a copy of it.
 *
 * @param analyzedPages the number of pages whose column was detected
 * @param referenceRecto the recto reference box, or null when the corpus had no reference
 * @param referenceVerso the verso reference box, or null when the corpus had no reference
 */
public record RunInfo(
        String paper,
        int dpi,
        int canvasWidth,
        int canvasHeight,
        boolean deskewEnabled,
        String anchor,
        double outlierRatio,
        int totalPages,
        int analyzedPages,
        @Nullable Box referenceRecto,
        @Nullable Box referenceVerso) {}
