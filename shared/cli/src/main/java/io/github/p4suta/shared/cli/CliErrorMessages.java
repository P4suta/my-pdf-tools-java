package io.github.p4suta.shared.cli;

import io.github.p4suta.shared.kernel.error.ErrorCategory;
import java.util.Map;
import java.util.Set;

/**
 * The CLI surface's English error catalog, keyed by the stable {@link ErrorCategory#name()}.
 *
 * <p>This is the CLI's own presentation layer: the shared kernel carries no user-facing text, so
 * the CLI resolves English here while the web UI resolves Japanese in its frontend. Keys are the
 * kind names, not the enum types, so one entry serves every category that shares a name (e.g.
 * {@code OUTPUT_CONFLICT} from the common, register and despeckle kinds) and a tool composing
 * several sub-tools (pdfbook) needs no compile dependency on each sub-domain's enum. An unknown
 * kind falls back to its bare name, so a newly added kind degrades to a readable token rather than
 * crashing — the per-tool {@code *MessagesCoverageTest} pins that every shipped kind has an entry.
 */
public final class CliErrorMessages {

    private static final Map<String, String> EN =
            Map.ofEntries(
                    // shared CommonErrorKind
                    Map.entry("INVALID_PARAMETER", "Invalid input value."),
                    Map.entry(
                            "OUTPUT_CONFLICT",
                            "The output already exists; pass --force to overwrite."),
                    Map.entry(
                            "OUT_OF_MEMORY",
                            "Out of memory. Increase -Xmx or try a PDF with fewer pages."),
                    Map.entry("INTERNAL", "An unexpected error occurred."),
                    // register / despeckle kinds
                    Map.entry("INPUT_NOT_FOUND", "Input file or directory not found."),
                    Map.entry(
                            "IMAGE_UNREADABLE",
                            "Could not read an image: unsupported format or corrupt file."),
                    Map.entry(
                            "NATIVE_TOOL_FAILED",
                            "An external tool failed. Check that the required tools (pdfimages /"
                                    + " pdfinfo / jbig2 / qpdf) are installed."),
                    // tate-yoko-pdf kinds
                    Map.entry("PDF_CORRUPTED", "Could not read the PDF; the file may be corrupt."),
                    Map.entry(
                            "PDF_PASSWORD_PROTECTED",
                            "The PDF is password-protected and cannot be processed."),
                    Map.entry("PDF_NOT_FOUND", "The specified PDF file was not found."),
                    Map.entry("PDF_INVALID_PAGE", "Invalid PDF page selection."),
                    Map.entry("PDF_WRITE_FAILED", "Failed to write the output PDF."));

    private CliErrorMessages() {}

    /**
     * {@return the English message for {@code kind}, or the kind's bare name when no entry exists}
     *
     * @param kind the resolved failure category
     */
    public static String of(ErrorCategory kind) {
        return EN.getOrDefault(kind.name(), kind.name());
    }

    /** {@return whether an English entry exists for the kind named {@code name}} */
    public static boolean has(String name) {
        return EN.containsKey(name);
    }

    /** {@return the set of kind names with an English entry (for coverage tests)} */
    public static Set<String> knownNames() {
        return EN.keySet();
    }
}
