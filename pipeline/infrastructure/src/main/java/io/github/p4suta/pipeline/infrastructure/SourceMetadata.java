package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.infrastructure.pdfbox.PdfBoxDocumentFactory;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the {@link DocumentMetadata} from a source scan PDF so the unified pipeline can carry the
 * book's title/author/etc. onto its output — the same fields the standalone tate CLI preserves.
 *
 * <p>The pipeline extracts page images via {@code pdfimages} and never otherwise opens the source
 * with PDFBox; this is a single, cheap structural read of the document information dictionary,
 * separate from the image extraction.
 *
 * <p>Best-effort: metadata is a nice-to-have, never a reason to fail a conversion, so any read
 * failure (an unreadable or encrypted source) is logged and falls back to {@link
 * DocumentMetadata#empty()}.
 */
public final class SourceMetadata {

    private static final Logger log = LoggerFactory.getLogger(SourceMetadata.class);

    private SourceMetadata() {}

    /**
     * Reads the document metadata of {@code sourcePdf}.
     *
     * @param sourcePdf the scan PDF the pipeline is converting
     * @return its metadata, or {@link DocumentMetadata#empty()} if it cannot be read
     */
    public static DocumentMetadata read(Path sourcePdf) {
        try (SourceDocument source = new PdfBoxDocumentFactory().openSource(sourcePdf)) {
            return source.metadata();
        } catch (RuntimeException e) {
            log.warn("could not read source metadata from {}: {}", sourcePdf, e.getMessage());
            return DocumentMetadata.empty();
        }
    }
}
