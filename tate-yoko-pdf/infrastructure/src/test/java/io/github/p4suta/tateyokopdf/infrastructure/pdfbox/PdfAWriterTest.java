package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.xml.DomXmpParser;
import org.junit.jupiter.api.Test;

final class PdfAWriterTest {

    @Test
    void stampAddsSrgbOutputIntentAndPdfAidMirroringInfo() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.getDocumentInformation().setTitle("題名");
            doc.getDocumentInformation().setProducer("tate-yoko-pdf");

            new PdfAWriter().stamp(doc);

            var intents = doc.getDocumentCatalog().getOutputIntents();
            assertThat(intents).hasSize(1);
            assertThat(intents.get(0).getOutputConditionIdentifier())
                    .isEqualTo("sRGB IEC61966-2.1");

            byte[] xmpBytes = doc.getDocumentCatalog().getMetadata().toByteArray();
            XMPMetadata xmp = new DomXmpParser().parse(new ByteArrayInputStream(xmpBytes));
            assertThat(xmp.getPDFAIdentificationSchema().getPart()).isEqualTo(2);
            assertThat(xmp.getPDFAIdentificationSchema().getConformance()).isEqualTo("B");
            assertThat(xmp.getDublinCoreSchema().getTitle()).isEqualTo("題名");
            assertThat(xmp.getAdobePDFSchema().getProducer()).isEqualTo("tate-yoko-pdf");
        }
    }
}
