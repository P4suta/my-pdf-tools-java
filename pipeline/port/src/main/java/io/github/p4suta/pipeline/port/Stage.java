package io.github.p4suta.pipeline.port;

import io.github.p4suta.pipeline.domain.Corpus;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A pipeline filter: transforms one {@link Corpus} into the next, writing its output under the
 * given working directory (e.g. despeckle, register). The uniform {@code Corpus -> Corpus} shape
 * lets a new processing step (crop, contrast, binarize, OCR, …) be added by implementing this one
 * interface and inserting it into the stage list — the runner, source, and sink are untouched.
 */
public interface Stage {

    /** {@return a short, filesystem-safe name used to label this stage's working directory} */
    String name();

    /**
     * Transforms {@code input}, writing its output page images under {@code workDir}.
     *
     * @param input the corpus produced by the previous step
     * @param workDir the directory this stage owns for its output
     * @return the transformed corpus
     * @throws IOException if processing fails
     */
    Corpus apply(Corpus input, Path workDir) throws IOException;
}
