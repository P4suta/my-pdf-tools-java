package io.github.p4suta.tateyokopdf.port;

import java.nio.file.Path;

/**
 * Optional post-processing pass over a freshly-written PDF (e.g. linearisation for Fast Web View).
 * Implementations operate in place — read {@code path} and overwrite it with the transformed
 * output.
 *
 * <p>{@link #noOp} is the safety fallback so the rest of the pipeline never has to special-case
 * "post-processor not configured": every site that takes a {@code PdfPostProcessor} can call {@link
 * #process} unconditionally.
 */
public interface PdfPostProcessor {

    void process(Path path);

    /** Pass-through implementation — useful as a default and in tests. */
    static PdfPostProcessor noOp() {
        return path -> {};
    }
}
