package io.github.p4suta.pipeline.port;

import io.github.p4suta.pipeline.domain.Corpus;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The pipeline exit: consumes the final {@link Corpus} and writes the single output artifact (e.g.
 * compose the RTL spread PDF — the only repack in the run). Implemented by an adapter in {@code
 * :pipeline:infrastructure}.
 */
public interface Sink {

    /**
     * Writes {@code input} to {@code output}.
     *
     * @param input the final corpus
     * @param output the file to write
     * @throws IOException if writing fails
     */
    void write(Corpus input, Path output) throws IOException;
}
