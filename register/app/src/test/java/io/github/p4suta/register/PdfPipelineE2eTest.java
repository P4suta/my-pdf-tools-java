package io.github.p4suta.register;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.p4suta.register.application.PdfPipelineService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.process.NativeTools;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end: a synthetic scan PDF goes through {@code register pipeline} to a lossless-JBIG2 PDF.
 * Gated on {@code pdfimages}/{@code jbig2} being installed (as {@code FlipbookE2eTest} gates on
 * {@code img2webp}), so CI without the tools skips it rather than failing.
 */
class PdfPipelineE2eTest {

    @Test
    void registersAPdfEndToEnd(@TempDir Path tmp) throws Exception {
        assumeTrue(toolsAvailable(), "pdfimages/jbig2/pdfinfo not installed");

        int pages = 4;
        int width = 413; // ~A6 width at 100 dpi (105 mm)
        int height = 583; // ~A6 height at 100 dpi (148 mm)
        int dpi = 100;
        Path source = tmp.resolve("source.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDImageXObject image =
                        LosslessFactory.createFromImage(doc, column(width, height, 120 + i));
                float widthPt = width / (float) dpi * 72f;
                float heightPt = height / (float) dpi * 72f;
                PDPage page = new PDPage(new PDRectangle(widthPt, heightPt));
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(image, 0, 0, widthPt, heightPt);
                }
            }
            doc.save(source.toFile());
        }

        Path out = tmp.resolve("out.pdf");
        RegisterOptions options =
                new RegisterOptions(OptionalInt.empty(), null, false, true, 0.5, Anchor.TOP_RIGHT);
        TestComposition.pdfPipelineService()
                .run(new PdfPipelineService.Config(source, out, options, 2, false));

        assertTrue(Files.exists(out), "output PDF written");
        assertTrue(startsWithPdfMagic(out), "output begins with %PDF");
        try (PDDocument result = Loader.loadPDF(out.toFile())) {
            assertEquals(pages, result.getNumberOfPages(), "every source page is registered");
        }
    }

    /** A white page with a single jittered black "text column". */
    private static BufferedImage column(int width, int height, int columnX) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.fillRect(columnX, 40, 180, height - 80);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static boolean startsWithPdfMagic(Path pdf) throws IOException {
        try (InputStream in = Files.newInputStream(pdf)) {
            byte[] magic = in.readNBytes(4);
            return magic.length == 4
                    && magic[0] == '%'
                    && magic[1] == 'P'
                    && magic[2] == 'D'
                    && magic[3] == 'F';
        }
    }

    private static boolean toolsAvailable() {
        try {
            NativeTools.pdfinfo();
            NativeTools.pdfimages();
            NativeTools.jbig2();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
