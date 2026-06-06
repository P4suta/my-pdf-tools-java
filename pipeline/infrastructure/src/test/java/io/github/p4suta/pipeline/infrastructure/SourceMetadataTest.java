package io.github.p4suta.pipeline.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit-tests the source-PDF metadata reader the pipeline uses to carry book info onto its output.
 */
class SourceMetadataTest {

    @Test
    void readsTheDocumentInformationFromTheSourcePdf(@TempDir Path tmp) throws IOException {
        Path pdf = tmp.resolve("book.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("吾輩は猫である");
            info.setAuthor("夏目漱石");
            info.setSubject("小説");
            doc.save(pdf.toFile());
        }

        DocumentMetadata metadata = SourceMetadata.read(pdf);

        assertThat(metadata.title()).contains("吾輩は猫である");
        assertThat(metadata.author()).contains("夏目漱石");
        assertThat(metadata.subject()).contains("小説");
    }

    @Test
    void fallsBackToEmptyWhenTheSourceCannotBeRead(@TempDir Path tmp) throws IOException {
        // A best-effort read: an unreadable source must never fail the conversion, only yield
        // empty.
        Path notPdf = tmp.resolve("broken.pdf");
        Files.writeString(notPdf, "this is not a pdf");

        assertThat(SourceMetadata.read(notPdf)).isEqualTo(DocumentMetadata.empty());
    }
}
