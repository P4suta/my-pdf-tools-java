package io.github.p4suta.pipeline.port;

import io.github.p4suta.pipeline.domain.Corpus;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The pipeline entry: materializes the initial {@link Corpus} into the given working directory
 * (e.g. extract a scan PDF's bitonal pages once). Implemented by an adapter in {@code
 * :pipeline:infrastructure}.
 */
public interface Source {

    /**
     * Produces the first corpus, writing its page images under {@code workDir}.
     *
     * @param workDir the directory this source owns for its output
     * @return the corpus describing the written pages
     * @throws IOException if extraction fails
     */
    Corpus open(Path workDir) throws IOException;
}
