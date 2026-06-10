package io.github.p4suta.shared.pdf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Wraps the raw CCITT G4 stream {@code pdfimages -ccitt} dumps into a single-strip TIFF — the
 * pass-through half of the extractor's remux mode: the scan's embedded G4 bytes become a readable
 * TIFF without ever being decoded and re-encoded.
 *
 * <p>poppler writes a fax2tiff-style {@code .params} file beside each dump ({@code -4} G4 / {@code
 * -1 -2} G3, {@code -A} EOL markers / {@code -P} none, {@code -X <columns>}, {@code -W} BlackIs1 /
 * {@code -B} not, {@code -M} MSB-first). Only the plain shape TIFF's T.6 compression can represent
 * verbatim is {@linkplain #supported supported}: G4, no EOL markers, MSB-first. Crucially, PDF's
 * {@code EncodedByteAlign} never reaches the params file, so a wrapped stream is only trusted after
 * the caller decodes it back successfully (see the extractor's read-back verification).
 */
final class CcittTiffs {

    private static final short TYPE_SHORT = 3;
    private static final short TYPE_LONG = 4;
    private static final short TYPE_RATIONAL = 5;

    private CcittTiffs() {}

    /**
     * The decode parameters poppler records beside a {@code .ccitt} dump.
     *
     * @param kind the coding scheme flag: {@code -4} (G4), {@code -2} (G3 2D) or {@code -1} (G3 1D)
     * @param endOfLine whether rows are prefixed with EOL markers ({@code -A})
     * @param columns the row width in pixels ({@code -X})
     * @param blackIs1 whether decoded 1-bits are black ({@code -W}) or 0-bits are ({@code -B})
     */
    record Params(String kind, boolean endOfLine, int columns, boolean blackIs1) {}

    /** Parse a {@code .params} file's text, or {@code null} when any token is unrecognized. */
    static @Nullable Params parseParams(String text) {
        @Nullable String kind = null;
        @Nullable Boolean endOfLine = null;
        @Nullable Integer columns = null;
        @Nullable Boolean blackIs1 = null;
        boolean msbFirst = false;
        String[] tokens = text.trim().split("\\s+", -1);
        for (int i = 0; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "-4", "-2", "-1" -> kind = tokens[i];
                case "-A" -> endOfLine = true;
                case "-P" -> endOfLine = false;
                case "-W" -> blackIs1 = true;
                case "-B" -> blackIs1 = false;
                case "-M" -> msbFirst = true;
                case "-X" -> {
                    i++;
                    if (i >= tokens.length) {
                        return null;
                    }
                    try {
                        columns = Integer.parseInt(tokens[i]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
        }
        if (kind == null || endOfLine == null || columns == null || blackIs1 == null || !msbFirst) {
            return null;
        }
        return new Params(kind, endOfLine, columns, blackIs1);
    }

    /**
     * Whether {@code params} describes a stream TIFF T.6 represents verbatim: Group 4, no EOL
     * markers, and a width agreeing with the listing row the dump corresponds to.
     */
    static boolean supported(Params params, int expectedWidth) {
        return "-4".equals(params.kind())
                && !params.endOfLine()
                && params.columns() == expectedWidth;
    }

    /**
     * Write {@code g4} as a little-endian, single-strip CCITT-G4 TIFF — header, the verbatim stream
     * as the one strip, then the IFD.
     *
     * @param out the TIFF to write
     * @param g4 the raw G4 (T.6) stream, verbatim
     * @param width the row width in pixels
     * @param height the row count
     * @param blackIs1 the params' photometric hint: decoded 1-bits are black ({@code -W})
     * @param dpi the resolution to stamp, or {@code <= 0} to omit the resolution tags
     */
    static void writeSingleStripG4(
            Path out, byte[] g4, int width, int height, boolean blackIs1, int dpi)
            throws IOException {
        boolean withResolution = dpi > 0;
        int entryCount = withResolution ? 14 : 11;
        int stripOffset = 8;
        int padding = g4.length % 2; // IFD offsets must be word-aligned
        int ifdOffset = stripOffset + g4.length + padding;
        int rationalOffset = ifdOffset + 2 + entryCount * 12 + 4;
        ByteBuffer buf =
                ByteBuffer.allocate(rationalOffset + (withResolution ? 16 : 0))
                        .order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(ifdOffset);
        buf.put(g4);
        if (padding == 1) {
            buf.put((byte) 0);
        }

        buf.putShort((short) entryCount); // entries below stay sorted by tag id
        entry(buf, 256, TYPE_LONG, width); // ImageWidth
        entry(buf, 257, TYPE_LONG, height); // ImageLength
        entryShort(buf, 258, 1); // BitsPerSample
        entryShort(buf, 259, 4); // Compression: CCITT T.6 (Group 4)
        // The G4 stream encodes white/black runs; this tag tells readers which sense to
        // materialize them in. The PDF default (-B, BlackIs1=false) is the standard fax sense —
        // TIFF WhiteIsZero (0); -W (BlackIs1=true) is the inverted sense, BlackIsZero (1).
        // Pinned empirically by CcittTiffsTest's pixel-identical round trip.
        entryShort(buf, 262, blackIs1 ? 1 : 0); // PhotometricInterpretation
        entryShort(buf, 266, 1); // FillOrder: MSB first (params -M)
        entry(buf, 273, TYPE_LONG, stripOffset); // StripOffsets
        entryShort(buf, 277, 1); // SamplesPerPixel
        entry(buf, 278, TYPE_LONG, height); // RowsPerStrip: the single strip
        entry(buf, 279, TYPE_LONG, g4.length); // StripByteCounts
        if (withResolution) {
            entry(buf, 282, TYPE_RATIONAL, rationalOffset); // XResolution
            entry(buf, 283, TYPE_RATIONAL, rationalOffset + 8); // YResolution
        }
        entry(buf, 293, TYPE_LONG, 0); // T6Options: none
        if (withResolution) {
            entryShort(buf, 296, 2); // ResolutionUnit: inch
        }
        buf.putInt(0); // no next IFD

        if (withResolution) {
            buf.putInt(dpi).putInt(1).putInt(dpi).putInt(1);
        }
        Files.write(out, buf.array());
    }

    /** One IFD entry holding an inline LONG (or a RATIONAL's value offset). */
    private static void entry(ByteBuffer buf, int tag, short type, int value) {
        buf.putShort((short) tag).putShort(type).putInt(1).putInt(value);
    }

    /** One IFD entry holding an inline SHORT (left-justified in the 4-byte value field). */
    private static void entryShort(ByteBuffer buf, int tag, int value) {
        buf.putShort((short) tag).putShort(TYPE_SHORT).putInt(1);
        buf.putShort((short) value).putShort((short) 0);
    }
}
