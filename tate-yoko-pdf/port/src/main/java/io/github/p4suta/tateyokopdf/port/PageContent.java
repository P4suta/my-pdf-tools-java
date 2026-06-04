package io.github.p4suta.tateyokopdf.port;

/**
 * Opaque handle to one source page's drawable content.
 *
 * <p>Deliberately empty: the application moves these handles from a {@link SourceDocument} to a
 * {@link SpreadDocument} without inspecting them, so the page representation stays entirely inside
 * the PDF adapter (the implementation is {@code PdfBoxPageContent}). A {@link SpreadDocument}
 * adapter downcasts to its own implementation when it imports the content.
 */
public interface PageContent {}
