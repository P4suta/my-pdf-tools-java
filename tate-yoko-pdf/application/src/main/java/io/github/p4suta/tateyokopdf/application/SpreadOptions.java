package io.github.p4suta.tateyokopdf.application;

import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The inputs to a single conversion: where to read, where to write, and how to lay out the spread.
 *
 * @param sourcePath the PDF to convert (non-null)
 * @param outputPath where to write the result (non-null)
 * @param direction the reading direction (non-null)
 * @param firstPageMode how page 1 opens (non-null)
 * @param pdfA whether to emit PDF/A-2b conformance structure
 */
public record SpreadOptions(
        Path sourcePath,
        Path outputPath,
        ReadingDirection direction,
        FirstPageMode firstPageMode,
        boolean pdfA) {
    public SpreadOptions {
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(firstPageMode, "firstPageMode must not be null");
    }

    /**
     * Options for {@code sourcePath} with every other field defaulted: output written to {@code
     * <name>_spread.pdf} beside the source, {@link ReadingDirection#DEFAULT} direction, {@link
     * FirstPageMode#STANDARD} opening, and no PDF/A.
     *
     * @param sourcePath the PDF to convert
     * @return default options for that source
     */
    public static SpreadOptions withDefaults(Path sourcePath) {
        return new SpreadOptions(
                sourcePath,
                deriveOutputPath(sourcePath),
                ReadingDirection.DEFAULT,
                FirstPageMode.STANDARD,
                false);
    }

    private static Path deriveOutputPath(Path source) {
        Path file = source.getFileName();
        if (file == null) {
            // Path.getFileName() only returns null for roots like "/" or "C:\\", which
            // we never legitimately receive as input — guard explicitly.
            throw new IllegalArgumentException("source path has no file name: " + source);
        }
        String output = file.toString().replaceFirst("(?i)\\.pdf$", "_spread.pdf");
        return source.resolveSibling(output);
    }
}
