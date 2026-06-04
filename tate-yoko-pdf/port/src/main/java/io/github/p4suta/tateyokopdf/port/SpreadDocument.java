package io.github.p4suta.tateyokopdf.port;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.SpreadSpec;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * A writable output PDF assembled one spread at a time, then finalized and saved. The known adapter
 * is {@code PdfBoxSpreadDocument}.
 *
 * <p>Not thread-safe. Expected lifecycle: {@link #addSpread} (repeatedly) → {@link #applyMetadata}
 * → optionally {@link #finalizePdfA} → {@link #save} → {@link #close}. The caller owns the instance
 * and must {@link #close()} it (try-with-resources).
 */
public interface SpreadDocument extends AutoCloseable {

    /**
     * Appends one spread page sized to {@code spec}, drawing each placement's content at its
     * position.
     *
     * @param spec the spread page's dimensions, in points
     * @param placements the pages to draw onto it (one for a single, two for a pair), each carrying
     *     its own offset within the frame
     */
    void addSpread(SpreadSpec spec, List<PagePlacement> placements);

    /**
     * Copy preserved fields from {@code source} into this document and stamp the output-specific
     * values ({@code modDate}, {@code producer}). {@code Optional.empty()} fields on {@code source}
     * leave the corresponding entry untouched — no empty-string overwrite.
     */
    void applyMetadata(DocumentMetadata source, Instant modDate, String producer);

    /**
     * Add the structures that mark this document as PDF/A-2b: an sRGB output intent and an XMP
     * packet carrying the {@code pdfaid} identification plus the standard Dublin Core / Adobe PDF /
     * XMP Basic properties.
     *
     * <p>Call this <em>after</em> {@link #applyMetadata} — the XMP packet is built by mirroring the
     * document information dictionary, so the two stay byte-for-byte consistent as PDF/A requires.
     *
     * <p>This adds conformance <em>structure</em> only; it does not rewrite page content. Whether
     * the output actually validates depends on the embedded source pages (fonts must be embedded,
     * colors device-independent or covered by the output intent).
     */
    void finalizePdfA();

    /**
     * Writes the assembled document to {@code destination}.
     *
     * @param destination the file path to write
     * @throws io.github.p4suta.tateyokopdf.domain.exception.SpreadException {@code
     *     PDF_WRITE_FAILED} if the file cannot be written
     */
    void save(Path destination);

    /** Releases the underlying document. Idempotent; never throws. */
    @Override
    void close();
}
