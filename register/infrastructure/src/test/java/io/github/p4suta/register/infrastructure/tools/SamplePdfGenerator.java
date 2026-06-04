package io.github.p4suta.register.infrastructure.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Generates a synthetic, copyright-free bitonal "scan" PDF for the smoke test and manual runs: a
 * few white pages, each carrying a single jittered black text column embedded as a 1-bpp image, so
 * {@code pdfimages} extracts it exactly like a real scan. It lets the project be exercised
 * end-to-end with zero external input — no real (copyrighted) scan is needed anywhere.
 *
 * <p>This deliberately lives in test sources (driven by the {@code createSamplePdf} Gradle task),
 * so the dev tool never ships in the production {@code register-all.jar}. It mirrors the synthetic
 * PDF the pipeline end-to-end test builds.
 */
public final class SamplePdfGenerator {

    private SamplePdfGenerator() {}

    /**
     * {@code SamplePdfGenerator <output.pdf> [pageCount]} — writes a synthetic bitonal scan PDF.
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "sample.pdf");
        int pages = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        write(out, pages);
    }

    /**
     * Write a {@code pages}-page synthetic bitonal scan to {@code out} (A6 geometry at 100 dpi).
     */
    public static void write(Path out, int pages) throws IOException {
        int width = 413; // ~A6 width at 100 dpi (105 mm)
        int height = 583; // ~A6 height at 100 dpi (148 mm)
        int dpi = 100;
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                // Jitter the column position per page so registration has something to correct.
                PDImageXObject image =
                        LosslessFactory.createFromImage(doc, column(width, height, 120 + i * 7));
                float widthPt = width / (float) dpi * 72f;
                float heightPt = height / (float) dpi * 72f;
                PDPage page = new PDPage(new PDRectangle(widthPt, heightPt));
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(image, 0, 0, widthPt, heightPt);
                }
            }
            doc.save(out.toFile());
        }
    }

    /** A white page with a single black "text column" — a 1-bpp (bitonal) image. */
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
}
