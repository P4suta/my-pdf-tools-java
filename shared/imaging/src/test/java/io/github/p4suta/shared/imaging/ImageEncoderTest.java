package io.github.p4suta.shared.imaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the AWT {@link BufferedImage} → file bridge: charts/overlays encode to readable WebP
 * (via Leptonica) and PNG. Requires the native Leptonica library, which the dev container bundles.
 */
final class ImageEncoderTest {

    private static BufferedImage sample() {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 40, 30);
            g.setColor(Color.RED);
            g.drawLine(0, 0, 39, 29);
        } finally {
            g.dispose();
        }
        return img;
    }

    @Test
    void writeWebpProducesReadableImage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("chart.webp");
        ImageEncoder.writeWebp(sample(), out);
        assertTrue(Files.size(out) > 0, "WebP must be written");
        try (Pix back = Pix.read(out)) {
            assertEquals(40, back.width());
            assertEquals(30, back.height());
        }
    }

    @Test
    void writePngProducesReadableImage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("chart.png");
        ImageEncoder.writePng(sample(), out);
        assertTrue(Files.size(out) > 0, "PNG must be written");
        try (Pix back = Pix.read(out)) {
            assertEquals(40, back.width());
            assertEquals(30, back.height());
        }
    }

    @Test
    void toBufferedImageReadsPixAsRgb(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("p.png");
        ImageEncoder.writePng(sample(), png);
        try (Pix pix = Pix.read(png)) {
            BufferedImage img = ImageEncoder.toBufferedImage(pix);
            assertEquals(40, img.getWidth());
            assertEquals(30, img.getHeight());
        }
    }
}
