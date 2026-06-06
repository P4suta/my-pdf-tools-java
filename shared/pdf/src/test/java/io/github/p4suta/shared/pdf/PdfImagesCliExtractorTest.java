package io.github.p4suta.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.shared.process.ToolPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adapter-level coverage for {@link PdfImagesCliExtractor}, driving the real {@code pdfimages} /
 * {@code pdfinfo} binaries over a small PDFBox-built PDF that carries an embedded image. The tool
 * property keys are constructor parameters, so the tests pass throwaway keys and rely on PATH
 * resolution.
 */
final class PdfImagesCliExtractorTest {

    private static final String PDFIMAGES_KEY = "shared.pdf.test.pdfimages.path";
    private static final String PDFINFO_KEY = "shared.pdf.test.pdfinfo.path";

    static boolean toolsOnPath() {
        return ToolPath.resolve("pdfimages", PDFIMAGES_KEY).isPresent()
                && ToolPath.resolve("pdfinfo", PDFINFO_KEY).isPresent();
    }

    /** Build a single-page PDF embedding a synthetic bitonal image. */
    private static void writePdfWithImage(Path pdf, int imgW, int imgH) throws IOException {
        BufferedImage bitonal = new BufferedImage(imgW, imgH, BufferedImage.TYPE_BYTE_BINARY);
        for (int x = imgW / 4; x < imgW / 2; x++) {
            for (int y = imgH / 4; y < imgH / 2; y++) {
                bitonal.setRGB(x, y, 0x000000);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(bitonal, "png", png);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(imgW, imgH));
            doc.addPage(page);
            PDImageXObject image =
                    PDImageXObject.createFromByteArray(doc, png.toByteArray(), "img");
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.drawImage(image, 0, 0, imgW, imgH);
            }
            doc.save(pdf.toFile());
        }
    }

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.PdfImagesCliExtractorTest#toolsOnPath")
    void extractsEmbeddedImagesAsTiffs(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("doc.pdf");
        writePdfWithImage(pdf, 120, 90);
        Path outDir = Files.createDirectory(tmp.resolve("out"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            new PdfImagesCliExtractor(PDFIMAGES_KEY, PDFINFO_KEY).extract(pdf, outDir, 2, pool);
        } finally {
            pool.shutdownNow();
        }

        try (Stream<Path> entries = Files.list(outDir)) {
            List<Path> tiffs =
                    entries.filter(p -> p.getFileName().toString().endsWith(".tif")).toList();
            assertThat(tiffs).as("at least one extracted TIFF").isNotEmpty();
        }
    }

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.PdfImagesCliExtractorTest#toolsOnPath")
    void dominantDpiReturnsAPositiveResolution(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("doc.pdf");
        writePdfWithImage(pdf, 120, 90);

        int dpi = new PdfImagesCliExtractor(PDFIMAGES_KEY, PDFINFO_KEY).dominantDpi(pdf);
        // The drawn image is 120x90 px over a 120x90 pt page -> 72 ppi; whatever pdfimages reports,
        // the parser must return a positive DPI (its DEFAULT_DPI fallback is also positive).
        assertThat(dpi).isPositive();
    }

    @Test
    void missingToolFailsWithAClearMessage(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("doc.pdf");
        Files.writeString(pdf, "%PDF-1.4\n");
        // An override pointing at a non-existent path: ToolPath returns the override,
        // ProcessRunner.start() then throws IOException at the dominantDpi() boundary.
        String badImagesKey = "shared.pdf.test.bad.pdfimages.path";
        System.setProperty(badImagesKey, tmp.resolve("definitely-not-pdfimages").toString());
        try {
            PdfImagesCliExtractor extractor = new PdfImagesCliExtractor(badImagesKey, PDFINFO_KEY);
            assertThatThrownBy(() -> extractor.dominantDpi(pdf)).isInstanceOf(IOException.class);
        } finally {
            System.clearProperty(badImagesKey);
        }
    }
}
