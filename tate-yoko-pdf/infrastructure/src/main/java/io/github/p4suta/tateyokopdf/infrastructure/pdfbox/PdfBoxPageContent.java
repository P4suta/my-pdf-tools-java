package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.port.PageContent;
import java.io.IOException;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * PDFBox-backed {@link PageContent}: a reference to one page of a source {@code PDDocument} that
 * the output adapter imports as a reusable form XObject.
 */
public class PdfBoxPageContent implements PageContent {

    private final PDDocument sourceDocument;
    private final int pageIndex;

    PdfBoxPageContent(PDDocument sourceDocument, int pageIndex) {
        this.sourceDocument = sourceDocument;
        this.pageIndex = pageIndex;
    }

    PDFormXObject importInto(PDDocument targetDocument) {
        try {
            var layerUtility = new LayerUtility(targetDocument);
            return layerUtility.importPageAsForm(sourceDocument, pageIndex);
        } catch (IOException e) {
            throw SpreadException.withDetail(ErrorKind.PDF_WRITE_FAILED, "page=" + pageIndex, e);
        }
    }
}
