package io.github.p4suta.pipeline.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end exercise of the pipeline adapters in the dev image (Leptonica + pdfimages + jbig2 +
 * PDFBox): a synthetic 4-page bitonal scan flows extract -> despeckle -> register -> spread, each
 * stage writing image files into its own directory, with the spread sink producing the only PDF.
 * Asserts the chain produces a valid landscape spread PDF (4 pages -> 2 spreads) and that the
 * inter-stage hand-off carries the dpi and the self-describing glob (tif -> tif -> tiff).
 */
class PipelineFlowTest {

    @TempDir Path tmp;

    @Test
    void extractDespeckleRegisterSpread() throws Exception {
        Path sample = tmp.resolve("sample-scan.pdf");
        try (InputStream in = PipelineFlowTest.class.getResourceAsStream("/sample-scan.pdf")) {
            assertThat(in).as("sample scan fixture on classpath").isNotNull();
            Files.copy(in, sample);
        }
        Path extracted = Files.createDirectories(tmp.resolve("00-extract"));
        Path cleaned = Files.createDirectories(tmp.resolve("01-clean"));
        Path registered = Files.createDirectories(tmp.resolve("02-register"));
        Path output = tmp.resolve("book.pdf");

        Corpus afterExtract = new PdfExtractSource(sample, 2).open(extracted);
        assertThat(afterExtract.pageCount()).isEqualTo(4);
        assertThat(afterExtract.glob()).isEqualTo("*.tif");
        assertThat(afterExtract.dpi()).isPositive();

        Corpus afterDespeckle = new DespeckleStage(2).apply(afterExtract, cleaned);
        assertThat(afterDespeckle.glob()).isEqualTo("*.tif");
        assertThat(afterDespeckle.dpi()).isEqualTo(afterExtract.dpi());

        Corpus afterRegister = new RegisterStage(2, true, true).apply(afterDespeckle, registered);
        assertThat(afterRegister.glob()).isEqualTo("*.tiff");

        new SpreadPackSink(
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false,
                        MemoryMode.IN_MEMORY,
                        DocumentMetadata.empty())
                .write(afterRegister, output);

        assertThat(Files.size(output)).isPositive();
        try (PDDocument doc = Loader.loadPDF(output.toFile())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            PDRectangle box = doc.getPage(0).getMediaBox();
            // Two portrait pages combined side by side -> a landscape spread.
            assertThat(box.getWidth()).isGreaterThan(box.getHeight());
        }
    }
}
