package io.github.p4suta.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.shared.imaging.Pix;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The CCITT remux building blocks: the fax2tiff-style params parser (pure) and the single-strip G4
 * TIFF writer, verified by wrapping a real G4 stream (PDFBox's CCITT encoder, the same encoding a
 * scanner PDF embeds) and decoding it back through Leptonica pixel-for-pixel.
 */
final class CcittTiffsTest {

    // ---- params parsing ----

    @Test
    void parsesTheUsualScannerShape() {
        CcittTiffs.Params params = parsed("-4 -P -X 3496 -B -M\n");
        assertThat(params).isEqualTo(new CcittTiffs.Params("-4", false, 3496, false));
        assertThat(CcittTiffs.supported(params, 3496)).isTrue();
    }

    @Test
    void eolMarkersAreUnsupported() {
        CcittTiffs.Params params = parsed("-4 -A -X 100 -W -M");
        assertThat(params).isEqualTo(new CcittTiffs.Params("-4", true, 100, true));
        // EOL markers are not representable in TIFF T.6.
        assertThat(CcittTiffs.supported(params, 100)).isFalse();
    }

    @Test
    void group3IsUnsupported() {
        assertThat(CcittTiffs.supported(parsed("-2 -P -X 100 -B -M"), 100)).isFalse();
    }

    @Test
    void widthMismatchIsUnsupported() {
        assertThat(CcittTiffs.supported(parsed("-4 -P -X 100 -B -M"), 200)).isFalse();
    }

    /** Parse params the test asserts are well-formed, made non-null for NullAway. */
    private static CcittTiffs.Params parsed(String text) {
        return java.util.Objects.requireNonNull(CcittTiffs.parseParams(text));
    }

    @Test
    void unknownTokensAndMissingFlagsAreUnparsable() {
        assertThat(CcittTiffs.parseParams("-4 -P -X 100 -B -M -Z")).isNull(); // unknown flag
        assertThat(CcittTiffs.parseParams("-4 -P -B -M")).isNull(); // no -X
        assertThat(CcittTiffs.parseParams("-4 -P -X nope -B -M")).isNull(); // bad width
        assertThat(CcittTiffs.parseParams("-4 -P -X 100 -B")).isNull(); // no -M
        assertThat(CcittTiffs.parseParams("")).isNull();
    }

    // ---- TIFF wrapping ----

    /**
     * Round trip: draw a known bitonal pattern, encode it to a raw G4 stream with PDFBox's CCITT
     * encoder (the very encoding a scanner PDF embeds and {@code pdfimages -ccitt} dumps), wrap the
     * stream with {@link CcittTiffs#writeSingleStripG4}, and assert it decodes back through
     * Leptonica pixel-identical to the original, with the stamped resolution.
     */
    @Test
    void wrappedStreamDecodesBackPixelIdentical(@TempDir Path tmp) throws Exception {
        int width = 200;
        int height = 150;
        BufferedImage img = pattern(width, height);
        G4Stream g4 = encodeG4(img);

        Path wrapped = tmp.resolve("wrapped.tif");
        CcittTiffs.writeSingleStripG4(wrapped, g4.bytes, width, height, g4.blackIs1, 450);

        Path referencePng = tmp.resolve("reference.png");
        ImageIO.write(img, "png", referencePng.toFile());
        try (Pix expected = Pix.read(referencePng);
                Pix actual = Pix.read(wrapped)) {
            assertThat(actual.width()).isEqualTo(width);
            assertThat(actual.height()).isEqualTo(height);
            assertThat(actual.resolution()).isEqualTo(450);
            assertThat(actual.blackPixels()).isPositive();
            assertThat(actual.pixelsEqual(expected)).isTrue();
        }
    }

    @Test
    void omitsResolutionTagsWhenDpiUnknown(@TempDir Path tmp) throws Exception {
        int width = 64;
        int height = 48;
        G4Stream g4 = encodeG4(pattern(width, height));

        Path wrapped = tmp.resolve("wrapped.tif");
        CcittTiffs.writeSingleStripG4(wrapped, g4.bytes, width, height, g4.blackIs1, 0);

        try (Pix actual = Pix.read(wrapped)) {
            assertThat(actual.width()).isEqualTo(width);
            assertThat(actual.resolution()).isZero();
        }
    }

    /** A raw G4 (T.6) stream and the {@code BlackIs1} convention its encoder declared. */
    private static final class G4Stream {
        final byte[] bytes;
        final boolean blackIs1;

        G4Stream(byte[] bytes, boolean blackIs1) {
            this.bytes = bytes;
            this.blackIs1 = blackIs1;
        }
    }

    /**
     * The raw CCITT G4 stream PDFBox's {@link CCITTFactory} encodes {@code img} to (lifted verbatim
     * from the image XObject, exactly the bytes {@code pdfimages -ccitt} would dump), along with
     * the {@code BlackIs1} decode parameter it declared.
     */
    private static G4Stream encodeG4(BufferedImage img) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject image = CCITTFactory.createFromImage(doc, img);
            COSDictionary decodeParms =
                    (COSDictionary) image.getCOSObject().getDictionaryObject(COSName.DECODE_PARMS);
            boolean blackIs1 =
                    decodeParms != null && decodeParms.getBoolean(COSName.BLACK_IS_1, false);
            try (InputStream in = image.getCOSObject().createRawInputStream()) {
                return new G4Stream(in.readAllBytes(), blackIs1);
            }
        }
    }

    /** A deterministic bitonal pattern with structure (bars + a block). */
    private static BufferedImage pattern(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            for (int x = 4; x < width - 8; x += 12) {
                g.fillRect(x, 8, 6, height - 16);
            }
            g.fillRect(width / 3, height / 3, width / 3, height / 3);
        } finally {
            g.dispose();
        }
        return img;
    }
}
