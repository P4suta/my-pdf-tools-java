package io.github.p4suta.tateyokopdf.port;

import java.nio.file.Path;

/**
 * Opens source PDFs and creates blank output documents — the hexagonal port that keeps the concrete
 * PDF engine out of the application layer.
 *
 * <p>The known adapter is {@code PdfBoxDocumentFactory}, which carries the configured output
 * version and memory mode so callers open and create documents without naming either.
 */
public interface DocumentFactory {

    /**
     * Opens the PDF at {@code path} for reading.
     *
     * @param path the source PDF to read
     * @return a read-only view the caller owns and must {@link SourceDocument#close() close}
     * @throws io.github.p4suta.tateyokopdf.domain.exception.SpreadException {@code
     *     PDF_PASSWORD_PROTECTED} if the file is encrypted, or {@code PDF_CORRUPTED} if it cannot
     *     be parsed
     */
    SourceDocument openSource(Path path);

    /**
     * Creates a fresh, empty output document to receive spreads.
     *
     * @return a writable document the caller owns and must {@link SpreadDocument#close() close}
     */
    SpreadDocument createOutput();
}
