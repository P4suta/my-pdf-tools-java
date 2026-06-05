package io.github.p4suta.shared.imaging;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Encodes an in-memory {@link BufferedImage} — the AWT charts and overlays the reports and
 * diagnostics draw — to a file, in the same raster-I/O family as {@link Pix}'s writes. WebP is the
 * standard for these human-facing artifacts (lossless, smaller than PNG, exact on line art); PNG is
 * the fallback.
 *
 * <p>WebP is produced by bridging through Leptonica: write a temporary PNG, read it back as a
 * {@link Pix}, and emit lossless WebP via {@link Pix#writeWebp}. So every still-image encode in the
 * monorepo — page rasters and BufferedImage charts alike — goes through this one imaging island,
 * and no second (shell-out) WebP encoder is needed. AWT lives behind this class; the FFM stays in
 * {@link Pix}/{@link Leptonica}.
 */
public final class ImageEncoder {

    private ImageEncoder() {}

    /**
     * Encode {@code image} as PNG to {@code path}.
     *
     * @throws IOException if no PNG writer is available or the write fails
     */
    public static void writePng(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("no PNG ImageIO writer available for " + path);
        }
    }

    /**
     * Encode {@code image} as lossless WebP to {@code path} (via a temporary PNG read back through
     * Leptonica).
     *
     * @throws IOException if the intermediate PNG cannot be written/read
     */
    public static void writeWebp(BufferedImage image, Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Path tmp =
                parent != null && Files.isDirectory(parent)
                        ? Files.createTempFile(parent, ".imgenc-", ".png")
                        : Files.createTempFile(".imgenc-", ".png");
        try {
            writePng(image, tmp);
            try (Pix pix = Pix.read(tmp)) {
                pix.writeWebp(path);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Read {@code pix} into an AWT {@link BufferedImage} (RGB) — the bridge for code that draws
     * overlays/charts with {@code Graphics2D} or compares pixels. Goes through a temporary PNG so
     * any Pix depth (1 bpp bilevel, 8 bpp grey, …) lands as a standard RGB raster, and the FFM
     * stays behind {@link Pix}.
     *
     * @param pix the image to read
     * @return an RGB {@link BufferedImage} copy
     * @throws IOException if the intermediate PNG cannot be written/read
     */
    public static BufferedImage toBufferedImage(Pix pix) throws IOException {
        Path tmp = Files.createTempFile(".imgenc-", ".png");
        try {
            pix.writePng(tmp);
            BufferedImage raw = ImageIO.read(tmp.toFile());
            if (raw == null) {
                throw new IOException("ImageIO could not read back " + tmp);
            }
            BufferedImage rgb =
                    new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.drawImage(raw, 0, 0, null);
            } finally {
                g.dispose();
            }
            return rgb;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
