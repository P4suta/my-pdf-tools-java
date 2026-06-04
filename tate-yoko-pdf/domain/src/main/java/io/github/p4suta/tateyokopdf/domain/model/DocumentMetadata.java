package io.github.p4suta.tateyokopdf.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Subset of a PDF's metadata that is preserved across the spread conversion. PDFBox-free pure value
 * object so the domain layer stays PDFBox-independent per the {@code domainIsPure} ArchUnit rule.
 *
 * <p>Scope is deliberately narrow: only fields whose meaning survives FormXObject-based page
 * embedding and page reordering. Outlines, annotations, page labels, form widgets, and XMP packet
 * are intentionally not represented here — they are page-bound and break under spread layout.
 */
public record DocumentMetadata(
        Optional<String> title,
        Optional<String> author,
        Optional<String> subject,
        Optional<String> keywords,
        Optional<String> creator,
        Optional<Instant> creationDate,
        Optional<String> language) {

    private static final DocumentMetadata EMPTY =
            new DocumentMetadata(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

    public static DocumentMetadata empty() {
        return EMPTY;
    }
}
