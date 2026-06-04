package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.tateyokopdf.testfixtures.PdfFixtures;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PdfBoxPageContentTest {

    @Test
    void importIntoReturnsFormXObject(@TempDir Path tmp) throws Exception {
        Path pdf = PdfFixtures.multiPageA4(tmp, "src.pdf", 1);
        try (PDDocument src = Loader.loadPDF(pdf.toFile());
                PDDocument tgt = new PDDocument()) {
            var content = new PdfBoxPageContent(src, 0);
            assertThat(content.importInto(tgt)).isNotNull();
        }
    }

    @Test
    void importIntoCanBeInvokedMultipleTimesIntoFreshTargets(@TempDir Path tmp) throws Exception {
        Path pdf = PdfFixtures.multiPageA4(tmp, "src.pdf", 2);
        try (PDDocument src = Loader.loadPDF(pdf.toFile())) {
            var content = new PdfBoxPageContent(src, 0);
            try (PDDocument tgt1 = new PDDocument()) {
                assertThat(content.importInto(tgt1)).isNotNull();
            }
            try (PDDocument tgt2 = new PDDocument()) {
                assertThat(content.importInto(tgt2)).isNotNull();
            }
        }
    }
}
