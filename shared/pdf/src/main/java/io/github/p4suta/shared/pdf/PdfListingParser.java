package io.github.p4suta.shared.pdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure parsers for the textual reports {@code pdfinfo} and {@code pdfimages -list} emit, with no
 * external process or filesystem dependency.
 */
public final class PdfListingParser {

    /** Fallback when the listing carries no usable resolution. */
    public static final int DEFAULT_DPI = 300;

    private PdfListingParser() {}

    /**
     * Parse the {@code Pages:} line of {@code pdfinfo} output.
     *
     * @param pdfinfoOutput the full text {@code pdfinfo} printed
     * @return the page count
     * @throws IllegalArgumentException if no parsable {@code Pages:} line is present
     */
    public static int parsePageCount(String pdfinfoOutput) {
        for (String line : pdfinfoOutput.split("\n", -1)) {
            if (line.startsWith("Pages:")) {
                String value = line.substring("Pages:".length()).trim();
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("unparsable pdfinfo Pages line: " + line, e);
                }
            }
        }
        throw new IllegalArgumentException("pdfinfo output had no Pages: line");
    }

    /**
     * The most common rounded x-ppi across the {@code image} rows of a {@code pdfimages -list}
     * report. Ties resolve to the first value seen and a non-positive winner falls back to {@link
     * #DEFAULT_DPI}.
     *
     * @param listOutput the full text {@code pdfimages -list} printed
     * @return the dominant rounded x-ppi, or {@link #DEFAULT_DPI} when none is usable
     */
    public static int parseDominantDpi(String listOutput) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (ImageRow row : parseImageRows(listOutput)) {
            counts.merge(row.xPpi(), 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return DEFAULT_DPI;
        }
        int best = 0;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best > 0 ? best : DEFAULT_DPI;
    }

    /**
     * One {@code image} row of a {@code pdfimages -list} report — the columns the extractor needs
     * to pick its mode and to wrap raw CCITT dumps.
     *
     * @param page the 1-based page the image sits on
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param bpc bits per component ({@code 1} for bitonal)
     * @param enc the embedded encoding token ({@code ccitt}, {@code jbig2}, {@code jpeg}, {@code
     *     image}, …)
     * @param xPpi the rounded x-ppi the image is placed at (0 when the cell is unusable)
     */
    public record ImageRow(int page, int width, int height, int bpc, String enc, int xPpi) {}

    /**
     * Parse the {@code image} rows of a {@code pdfimages -list} report, in listing order (the same
     * order {@code pdfimages} dumps the images in), skipping the two header rows and any row with
     * unparsable numeric cells.
     *
     * @param listOutput the full text {@code pdfimages -list} printed
     * @return the parsed rows, possibly empty
     */
    public static List<ImageRow> parseImageRows(String listOutput) {
        String[] lines = listOutput.split("\n", -1);
        List<ImageRow> rows = new ArrayList<>();
        for (int i = 2; i < lines.length; i++) {
            String[] fields = lines[i].trim().split("\\s+", -1);
            if (fields.length < 13 || !"image".equals(fields[2])) {
                continue;
            }
            try {
                rows.add(
                        new ImageRow(
                                Integer.parseInt(fields[0]),
                                Integer.parseInt(fields[3]),
                                Integer.parseInt(fields[4]),
                                Integer.parseInt(fields[7]),
                                fields[8],
                                (int) Math.round(Double.parseDouble(fields[12]))));
            } catch (NumberFormatException ignored) {
                // A non-numeric cell: skip this row.
            }
        }
        return rows;
    }
}
