package io.github.p4suta.pipeline.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Generates the synthetic, copyright-free bitonal "scan" book the {@code benchPipeline} harness
 * converts: A5 pages at the requested dpi carrying vertical text-like columns with per-page
 * position jitter, a small per-page skew of up to ±0.5° (so the register stage's deskew has real
 * work) and salt-and-pepper specks (so despeckle has real work), embedded as CCITT G4 so {@code
 * pdfimages} extracts them exactly like a real scan. A fixed seed keeps every generation
 * byte-identical, so benchmark runs stay comparable across machines and branches.
 *
 * <p>This deliberately lives in test sources (driven by the {@code createSampleScan} Gradle task),
 * mirroring register's {@code SamplePdfGenerator}: the dev tool never ships in the production
 * launcher. An existing output is reused, so repeated benchmark runs skip the generation cost.
 *
 * <p>Usage: {@code SampleScanGenerator <output.pdf> [pages] [dpi]}
 */
public final class SampleScanGenerator {

    private SampleScanGenerator() {}

    /** {@code SampleScanGenerator <output.pdf> [pages] [dpi]} — writes the synthetic scan book. */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "sample-scan.pdf");
        int pages = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int dpi = args.length > 2 ? Integer.parseInt(args[2]) : 600;
        if (Files.isRegularFile(out)) {
            System.out.println("reusing existing " + out + " (delete it to regenerate)");
            return;
        }
        long start = System.nanoTime();
        write(out, pages, dpi);
        System.out.printf(
                Locale.ROOT,
                "wrote %s: %d page(s) at %d dpi, %.1f MiB in %.1fs%n",
                out,
                pages,
                dpi,
                Files.size(out) / (1024.0 * 1024.0),
                (System.nanoTime() - start) / 1e9);
    }

    /** Writes a {@code pages}-page synthetic bitonal scan book to {@code out} (A5 geometry). */
    public static void write(Path out, int pages, int dpi) throws IOException {
        int width = Math.round(148f * dpi / 25.4f); // A5 portrait: 148 mm × 210 mm
        int height = Math.round(210f * dpi / 25.4f);
        Random random = new Random(42);
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PDDocument doc = new PDDocument()) {
            float widthPt = width * 72f / dpi;
            float heightPt = height * 72f / dpi;
            for (int i = 0; i < pages; i++) {
                PDImageXObject image =
                        CCITTFactory.createFromImage(doc, page(width, height, random));
                PDPage page = new PDPage(new PDRectangle(widthPt, heightPt));
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(image, 0, 0, widthPt, heightPt);
                }
            }
            doc.save(out.toFile());
        }
    }

    /** One page: slightly skewed text-like columns plus unrotated scanner-dust specks. */
    private static BufferedImage page(int width, int height, Random random) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            double skew = Math.toRadians(random.nextDouble() - 0.5); // ±0.5°
            g.rotate(skew, width / 2.0, height / 2.0);
            drawColumns(g, width, height, random);
            g.rotate(-skew, width / 2.0, height / 2.0);
            drawSpecks(g, width, height, random);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Vertical "text" columns right-to-left (Japanese book layout): stacked glyph-sized blocks with
     * per-page jitter so registration has a real column position to detect and correct, and random
     * early line breaks so the texture resembles prose rather than a solid block.
     */
    private static void drawColumns(Graphics2D g, int width, int height, Random random) {
        int margin = width / 10;
        int glyph = Math.max(4, width / 60);
        int leading = glyph / 2;
        int jitterX = random.nextInt(glyph + 1) - glyph / 2;
        int top = height / 12 + random.nextInt(glyph + 1);
        int bottom = height - height / 12;
        for (int x = width - margin - glyph + jitterX; x >= margin; x -= glyph + leading) {
            int y = top;
            while (y + glyph <= bottom) {
                // ~8% of glyph slots end the "sentence" early, leaving prose-like white runs.
                if (random.nextInt(100) < 8) {
                    y += glyph * (2 + random.nextInt(4));
                    continue;
                }
                g.fillRect(x, y, glyph - 2, glyph - 2);
                y += glyph;
            }
        }
    }

    /** Salt-and-pepper dust: ~1 speck of 1–3 px per 25k pixels, what despeckle exists to remove. */
    private static void drawSpecks(Graphics2D g, int width, int height, Random random) {
        int specks = width * height / 25_000;
        for (int i = 0; i < specks; i++) {
            int size = 1 + random.nextInt(3);
            g.fillRect(random.nextInt(width - size), random.nextInt(height - size), size, size);
        }
    }
}
