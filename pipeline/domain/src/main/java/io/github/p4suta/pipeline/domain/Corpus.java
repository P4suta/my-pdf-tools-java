package io.github.p4suta.pipeline.domain;

import java.nio.file.Path;

/**
 * An ordered set of page images in a working directory — the value that flows between pipeline
 * stages (pipes-and-filters). Self-describing: {@code glob} states the filename pattern of the
 * images this corpus holds, so each stage reads its predecessor's output with the right pattern
 * without any cross-stage assumption (despeckle writes {@code *.tif}, register writes {@code
 * *.tiff}).
 *
 * <p>A pure value type — it names the working directory but performs no I/O, so it stays within the
 * domain layer's filesystem-free rule. Pages are the files in {@code dir} matching {@code glob}, in
 * filename order (== reading order, since the extractor names pages with a zero-padded index).
 *
 * @param dir the directory holding this corpus's page images
 * @param glob the filename glob matching the page images (e.g. {@code *.tif})
 * @param dpi the resolved scan resolution in dots per inch (positive)
 * @param pageCount the number of pages (non-negative)
 */
public record Corpus(Path dir, String glob, int dpi, int pageCount) {

    public Corpus {
        if (glob.isBlank()) {
            throw new IllegalArgumentException("glob must not be blank");
        }
        if (dpi <= 0) {
            throw new IllegalArgumentException("dpi must be positive: " + dpi);
        }
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must not be negative: " + pageCount);
        }
    }

    /**
     * Returns a copy describing a stage's output: the same page count and dpi, but a new directory
     * and the glob of the format that stage wrote.
     *
     * @param newDir the stage's output directory
     * @param newGlob the glob matching the files the stage wrote
     * @return the successor corpus
     */
    public Corpus movedTo(Path newDir, String newGlob) {
        return new Corpus(newDir, newGlob, dpi, pageCount);
    }
}
