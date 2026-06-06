package io.github.p4suta.despeckle.domain.service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Pure output-naming helpers for the PDF batch: deriving each cleaned book's file name and its
 * report sub-directory stem from the input path. Lifted out of the batch service so the naming
 * rules can be unit-tested without touching the filesystem.
 */
public final class PdfOutputNaming {

    private PdfOutputNaming() {}

    /**
     * The output file name for {@code input}: its stem plus {@code suffix} plus {@code .pdf}. An
     * empty suffix keeps the original name (extension case included); a non-empty suffix normalizes
     * the extension to lower-case {@code .pdf}.
     */
    public static String outputName(Path input, String suffix) {
        String name = Objects.requireNonNull(input.getFileName()).toString();
        if (suffix.isEmpty()) {
            return name;
        }
        return stripPdf(name) + suffix + ".pdf";
    }

    /** The input's file name without its {@code .pdf} extension (case-insensitive). */
    public static String stem(Path input) {
        return stripPdf(Objects.requireNonNull(input.getFileName()).toString());
    }

    /** Strip the trailing {@code .pdf} extension from {@code name} (any case). */
    public static String stripPdf(String name) {
        return name.substring(0, name.length() - ".pdf".length());
    }
}
