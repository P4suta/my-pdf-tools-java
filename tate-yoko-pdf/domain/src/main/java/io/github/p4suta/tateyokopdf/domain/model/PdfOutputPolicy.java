package io.github.p4suta.tateyokopdf.domain.model;

/**
 * Single source of truth for the PDF version this app emits. Flip {@link #TARGET} to upgrade — both
 * the PDFBox catalog entry and the qpdf header-byte rewrite read from here, so a one-line change
 * propagates to every output path.
 *
 * <p>Locked to {@link PdfVersion#PDF_1_7} (ISO 32000-1:2008) for universal viewer compatibility.
 * PDF 2.0 is reserved for a future flip — PDFBox 3.0.7's PDF 2.0 emit support is currently limited
 * to catalog/header bytes (no UTF-8 string metadata or other 2.0-specific structures).
 */
public final class PdfOutputPolicy {

    public static final PdfVersion TARGET = PdfVersion.PDF_1_7;

    private PdfOutputPolicy() {}
}
