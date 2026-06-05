package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.LayoutPosition;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.PageDimension;
import io.github.p4suta.tateyokopdf.domain.model.PdfVersion;
import io.github.p4suta.tateyokopdf.domain.model.SpreadSpec;
import io.github.p4suta.tateyokopdf.port.PagePlacement;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import io.github.p4suta.tateyokopdf.port.SpreadDocument;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the image-backed spread adapter: an {@link ImageDirDocumentFactory} reads register's
 * TIFF-G4 pages, reports their size in points, and {@link PdfBoxSpreadDocument} embeds them as
 * CCITT G4 into a landscape spread page — the path that lets the pipeline compose spreads with no
 * intermediate PDF. Fixtures are two synthetic registered pages (register's own copyright-free
 * sample), so dimensions are asserted relatively rather than against a fixed paper size.
 */
class ImageDirSpreadTest {

    private static final int DPI = 72;

    @TempDir Path dir;

    @Test
    void reportsPointDimensionsAndEmbedsCcittSpread() throws Exception {
        copyFixture("page-000.tiff");
        copyFixture("page-001.tiff");

        var factory =
                new ImageDirDocumentFactory(
                        "*.tiff",
                        DPI,
                        DocumentMetadata.empty(),
                        PdfVersion.PDF_1_7,
                        MemoryMode.IN_MEMORY);

        Path out = dir.resolve("spread.pdf");
        float pageWidthPt;
        float pageHeightPt;
        try (SourceDocument source = factory.openSource(dir)) {
            assertThat(source.pageCount()).isEqualTo(2);

            PageDimension d0 = source.pageDimension(0);
            assertThat(d0.widthPt()).isPositive();
            assertThat(d0.heightPt()).isGreaterThan(d0.widthPt()); // a portrait scan page
            pageWidthPt = d0.widthPt();
            pageHeightPt = d0.heightPt();

            try (SpreadDocument output = factory.createOutput()) {
                output.addSpread(
                        new SpreadSpec(pageWidthPt * 2f, pageHeightPt),
                        List.of(
                                new PagePlacement(
                                        source.pageContent(0), new LayoutPosition(0f, 0f)),
                                new PagePlacement(
                                        source.pageContent(1),
                                        new LayoutPosition(pageWidthPt, 0f))));
                output.save(out);
            }
        }

        assertThat(Files.size(out)).isPositive();
        try (PDDocument reopened = Loader.loadPDF(out.toFile())) {
            assertThat(reopened.getNumberOfPages()).isEqualTo(1);
            PDRectangle box = reopened.getPage(0).getMediaBox();
            // Two portrait pages side by side -> a landscape spread twice the page width.
            assertThat(box.getWidth()).isEqualTo(pageWidthPt * 2f, within(1f));
            assertThat(box.getHeight()).isEqualTo(pageHeightPt, within(1f));
            assertThat(box.getWidth()).isGreaterThan(box.getHeight());
        }
    }

    private void copyFixture(String name) throws Exception {
        try (InputStream in =
                ImageDirSpreadTest.class.getResourceAsStream("/imagespread/" + name)) {
            assertThat(in).as("fixture %s on classpath", name).isNotNull();
            Files.copy(in, dir.resolve(name));
        }
    }
}
