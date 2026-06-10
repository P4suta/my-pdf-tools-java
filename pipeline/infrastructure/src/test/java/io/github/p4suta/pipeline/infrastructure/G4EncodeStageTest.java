package io.github.p4suta.pipeline.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.pipeline.domain.Corpus;
import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.shared.progress.ProgressEvent.PageProcessed;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link G4EncodeStage} against the failure that motivated it: {@code pdfimages -tiff}
 * writes non-G4 TIFFs, and the spread sink's pass-through {@link CCITTFactory} embedding requires
 * single-strip CCITT G4. The fixture pages are written as ImageIO's default (uncompressed) bitonal
 * TIFF — the same not-G4 shape poppler produces — and the core assertion is the sink's own
 * contract: {@code CCITTFactory.createFromFile} accepts every encoded page. Requires the native
 * Leptonica library, which the dev container bundles.
 */
class G4EncodeStageTest {

    private static final int DPI = 600;

    @TempDir Path tmp;

    @Test
    void reEncodesUncompressedTiffsAsCcittG4() throws Exception {
        Path inputDir = Files.createDirectories(tmp.resolve("in"));
        Path workDir = Files.createDirectories(tmp.resolve("out"));
        int pages = 3;
        for (int i = 0; i < pages; i++) {
            writeUncompressedBitonalTiff(
                    inputDir.resolve(String.format(Locale.ROOT, "page-%03d.tif", i)), i);
        }
        List<ProgressEvent> events = Collections.synchronizedList(new ArrayList<>());
        Corpus input = new Corpus(inputDir, "*.tif", DPI, pages);

        Corpus output = new G4EncodeStage(2, events::add).apply(input, workDir);

        assertThat(output.dir()).isEqualTo(workDir);
        assertThat(output.glob()).isEqualTo("*.tif");
        assertThat(output.dpi()).isEqualTo(DPI);
        assertThat(output.pageCount()).isEqualTo(pages);
        try (var files = Files.list(workDir)) {
            assertThat(files.filter(p -> p.toString().endsWith(".tif"))).hasSize(pages);
        }
        for (int i = 0; i < pages; i++) {
            String name = String.format(Locale.ROOT, "page-%03d.tif", i);
            Path encoded = workDir.resolve(name);
            // The sink's exact contract: PDFBox's CCITT pass-through embedder accepts the page
            // (it rejects the uncompressed input fixture with an IOException).
            try (PDDocument doc = new PDDocument()) {
                PDImageXObject xobject = CCITTFactory.createFromFile(doc, encoded.toFile());
                assertThat(xobject.getWidth()).isPositive();
            }
            // The corpus dpi is stamped (pdfimages tags extracted pages at a default 72 dpi)...
            try (Pix page = Pix.read(encoded)) {
                assertThat(page.resolution()).isEqualTo(DPI);
            }
            // ...and G4 is lossless for 1 bpp: the pixels survive the re-encode unchanged.
            try (Pix before = Pix.read(inputDir.resolve(name));
                    Pix after = Pix.read(encoded)) {
                assertThat(after.pixelsEqual(before)).isTrue();
            }
        }
        assertProgress(events, pages);
    }

    @Test
    void surfacesAnUnreadablePageWithItsOriginalException() throws Exception {
        Path inputDir = Files.createDirectories(tmp.resolve("in"));
        Path workDir = Files.createDirectories(tmp.resolve("out"));
        Files.writeString(inputDir.resolve("page-000.tif"), "not a tiff");
        Corpus input = new Corpus(inputDir, "*.tif", DPI, 1);

        // The fan-out preserves the failure's identity (no generic IOException wrapper), so the
        // shared exception mapper still sees the original type and the message names the page.
        assertThatThrownBy(() -> new G4EncodeStage(2).apply(input, workDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not read image");
    }

    /**
     * A 1-bit page written through ImageIO's TIFF writer, whose default is uncompressed — the same
     * non-G4 shape {@code pdfimages -tiff} produces. A black bar at a per-page row makes each page
     * distinct so the pixel-fidelity assertion is meaningful.
     */
    private static void writeUncompressedBitonalTiff(Path file, int index) throws Exception {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_BYTE_BINARY);
        var g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 120, 80);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(10, 10 + index * 15, 100, 10);
        g.dispose();
        assertThat(ImageIO.write(image, "tiff", file.toFile()))
                .as("java.desktop TIFF writer available")
                .isTrue();
    }

    /** One {@code PageProcessed} per page, stage {@code "encode"}, with done reaching the total. */
    private static void assertProgress(List<ProgressEvent> events, int total) {
        List<PageProcessed> forStage =
                events.stream()
                        .filter(e -> e instanceof PageProcessed p && p.stage().equals("encode"))
                        .map(PageProcessed.class::cast)
                        .toList();
        assertThat(forStage).hasSize(total);
        assertThat(forStage).allSatisfy(p -> assertThat(p.total()).isEqualTo(total));
        assertThat(forStage.stream().mapToInt(PageProcessed::done).max().orElse(-1))
                .isEqualTo(total);
    }
}
