package io.github.p4suta.tateyokopdf.domain.model;

/**
 * PDF specification versions this app can emit. Pure value object — no PDFBox or qpdf coupling, so
 * domain-layer purity rules in {@code LayerDependencyTest} continue to hold.
 *
 * <p>{@link #headerValue()} feeds {@code PDDocument#setVersion(float)} and {@link #label()} feeds
 * the {@code qpdf --min-version=X.Y} CLI flag. Both views are needed because PDFBox 3.0.7's {@code
 * setVersion} only updates the catalog {@code /Version} entry (not the {@code %PDF-x.x} header
 * byte) for any value &ge; 1.4, so the header byte must be patched by qpdf separately.
 */
public enum PdfVersion {
    PDF_1_7(1.7f, "1.7"),
    PDF_2_0(2.0f, "2.0");

    private final float headerValue;
    private final String label;

    PdfVersion(float headerValue, String label) {
        this.headerValue = headerValue;
        this.label = label;
    }

    /** Numeric form for {@code PDDocument#setVersion(float)}. */
    public float headerValue() {
        return headerValue;
    }

    /** Decimal-string form for {@code qpdf --min-version=X.Y}. */
    public String label() {
        return label;
    }
}
