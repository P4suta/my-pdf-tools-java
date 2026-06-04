package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.PageDimension;
import io.github.p4suta.tateyokopdf.port.PageContent;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PDFBox-backed {@link SourceDocument}: a read-only wrapper over a loaded {@code PDDocument}. */
public class PdfBoxSourceDocument implements SourceDocument {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxSourceDocument.class);

    // A page rotated a quarter or three-quarter turn presents its cropBox sideways, so the
    // displayed
    // width/height are swapped relative to the unrotated box.
    private static final int QUARTER_TURN_DEGREES = 90;
    private static final int THREE_QUARTER_TURN_DEGREES = 270;

    private final PDDocument document;

    PdfBoxSourceDocument(PDDocument document) {
        this.document = document;
    }

    @Override
    public int pageCount() {
        return document.getNumberOfPages();
    }

    @Override
    public PageDimension pageDimension(int index) {
        PDPage page = document.getPage(index);
        PDRectangle cropBox = page.getCropBox();
        int rotation = page.getRotation();

        float width = cropBox.getWidth();
        float height = cropBox.getHeight();

        if (swapsDimensions(rotation)) {
            log.debug("Page {} has rotation={}, swapping dimensions", index, rotation);
            return new PageDimension(height, width);
        }

        return new PageDimension(width, height);
    }

    private static boolean swapsDimensions(int rotation) {
        return rotation == QUARTER_TURN_DEGREES || rotation == THREE_QUARTER_TURN_DEGREES;
    }

    @Override
    public PageContent pageContent(int index) {
        return new PdfBoxPageContent(document, index);
    }

    @Override
    public DocumentMetadata metadata() {
        PDDocumentInformation info = document.getDocumentInformation();
        String language = document.getDocumentCatalog().getLanguage();
        return new DocumentMetadata(
                Optional.ofNullable(info.getTitle()),
                Optional.ofNullable(info.getAuthor()),
                Optional.ofNullable(info.getSubject()),
                Optional.ofNullable(info.getKeywords()),
                Optional.ofNullable(info.getCreator()),
                Optional.ofNullable(info.getCreationDate()).map(c -> c.toInstant()),
                Optional.ofNullable(language));
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (Exception e) {
            log.warn("Failed to close source document", e);
        }
    }
}
