package io.github.p4suta.tateyokopdf.port;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.PageDimension;

/**
 * A read-only view of a source PDF: page count, per-page geometry and content, and document
 * metadata. The known adapter is {@code PdfBoxSourceDocument}.
 *
 * <p>Not thread-safe — use one instance from a single thread. Page indices are zero-based and valid
 * for {@code 0 <= index < pageCount()}. The caller owns the instance and must {@link #close()} it
 * (try-with-resources); access after {@code close()} is undefined.
 */
public interface SourceDocument extends AutoCloseable {

    /** {@return the number of pages in the source document} */
    int pageCount();

    /**
     * Returns the displayed size of a page, with page rotation already applied (a page rotated a
     * quarter or three-quarter turn reports swapped width/height).
     *
     * @param index zero-based page index
     * @return the page's displayed width and height, in points
     */
    PageDimension pageDimension(int index);

    /**
     * Returns a handle to a page's drawable content for placement into an output spread.
     *
     * @param index zero-based page index
     * @return an opaque {@link PageContent} the matching {@link SpreadDocument} adapter can import
     */
    PageContent pageContent(int index);

    /** {@return the source document's metadata, with absent fields as {@code Optional.empty()}} */
    DocumentMetadata metadata();

    /** Releases the underlying document. Idempotent; never throws. */
    @Override
    void close();
}
