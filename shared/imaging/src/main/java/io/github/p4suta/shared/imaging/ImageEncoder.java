package io.github.p4suta.shared.imaging;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Encodes an in-memory {@link BufferedImage} to a file as PNG or lossless WebP. WebP is produced by
 * bridging through Leptonica: write a temporary PNG, read it back as a {@link Pix}, and emit
 * lossless WebP via {@link Pix#writeWebp}. AWT lives behind this class; the FFM stays in {@link
 * Pix}/{@link Leptonica}.
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
     * Read {@code pix} into an RGB AWT {@link BufferedImage}. Goes through a temporary PNG so any
     * Pix depth (1 bpp bilevel, 8 bpp grey, …) lands as a standard RGB raster.
     *
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
