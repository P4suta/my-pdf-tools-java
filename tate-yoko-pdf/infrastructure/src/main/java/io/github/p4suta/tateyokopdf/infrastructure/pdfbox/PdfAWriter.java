package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.function.Consumer;
import javax.xml.transform.TransformerException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;
import org.jspecify.annotations.Nullable;

/**
 * Stamps the PDF/A-2b conformance structure onto a document whose metadata has already been
 * applied: an sRGB output intent plus an XMP packet (pdfaid + Dublin Core / Adobe PDF / XMP Basic)
 * mirroring the info dictionary.
 *
 * <p>Confined to the PDFBox adapter package — it manipulates {@code PDDocument}, {@code
 * PDOutputIntent}, and XMPBox types directly. The PDF-version guard and the mapping of failures
 * onto the domain error type stay with {@link PdfBoxSpreadDocument}, which owns those invariants.
 */
final class PdfAWriter {

    private static final String SRGB_CONDITION = "sRGB IEC61966-2.1";
    private static final String COLOR_REGISTRY = "http://www.color.org";

    // PDF/A-2, conformance level B (the "basic" level — visual reproducibility, no tagging).
    private static final int PDF_A_PART = 2;
    private static final String PDF_A_CONFORMANCE = "B";

    /** Adds the sRGB output intent and the pdfaid/XMP packet to {@code document}. */
    void stamp(PDDocument document)
            throws IOException, TransformerException, BadFieldValueException {
        addSrgbOutputIntent(document);
        addPdfAMetadata(document);
    }

    /**
     * PDF/A requires an output intent so device color is reproducible. The JDK's built-in sRGB
     * profile avoids bundling an .icc resource (and the shadow-jar / jpackage plumbing that
     * entails); {@code java.desktop} is already a bundled module because PDFBox needs it.
     */
    private void addSrgbOutputIntent(PDDocument document) throws IOException {
        ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        PDOutputIntent intent =
                new PDOutputIntent(document, new ByteArrayInputStream(srgb.getData()));
        intent.setInfo(SRGB_CONDITION);
        intent.setOutputCondition(SRGB_CONDITION);
        intent.setOutputConditionIdentifier(SRGB_CONDITION);
        intent.setRegistryName(COLOR_REGISTRY);
        document.getDocumentCatalog().addOutputIntent(intent);
    }

    /**
     * Builds the XMP packet by mirroring the document information dictionary the caller already
     * populated. Reading the values back from the Info dictionary (rather than from the original
     * source) means both representations come from the same second-resolution PDF date strings and
     * string values, so PDF/A's Info/XMP consistency rule holds by construction.
     */
    private void addPdfAMetadata(PDDocument document)
            throws IOException, TransformerException, BadFieldValueException {
        PDDocumentInformation info = document.getDocumentInformation();
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();

        PDFAIdentificationSchema pdfaid = xmp.createAndAddPDFAIdentificationSchema();
        pdfaid.setPart(PDF_A_PART);
        pdfaid.setConformance(PDF_A_CONFORMANCE);

        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        ifPresent(info.getTitle(), dc::setTitle);
        ifPresent(info.getAuthor(), dc::addCreator);
        ifPresent(info.getSubject(), dc::setDescription);

        AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
        ifPresent(info.getProducer(), pdf::setProducer);
        ifPresent(info.getKeywords(), pdf::setKeywords);

        XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
        ifPresent(info.getCreator(), basic::setCreatorTool);
        Calendar creationDate = info.getCreationDate();
        if (creationDate != null) {
            basic.setCreateDate(creationDate);
        }
        Calendar modificationDate = info.getModificationDate();
        if (modificationDate != null) {
            basic.setModifyDate(modificationDate);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new XmpSerializer().serialize(xmp, out, true);
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(out.toByteArray());
        document.getDocumentCatalog().setMetadata(metadata);
    }

    private static void ifPresent(@Nullable String value, Consumer<String> setter) {
        if (value != null && !value.isEmpty()) {
            setter.accept(value);
        }
    }
}
