package io.github.p4suta.shared.pdf;

import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.process.ProcessRunner;
import io.github.p4suta.shared.process.Tasks;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Extracts a PDF's embedded bitonal images as TIFFs by driving {@code pdfimages}. The page range is
 * split across the worker pool (one {@code pdfimages -f/-l} per chunk) with distinct zero-padded
 * {@code page-cNN-} prefixes, so a name sort yields reading order and chunks never collide. An
 * all-CCITT source is remuxed — the raw embedded G4 streams pass through into single-strip TIFFs
 * with their true ppi stamped (see {@link #extract}); any other source is extracted decoded, where
 * {@code pdfimages} tags the TIFFs at a default 72 dpi, so the dominant scan DPI from {@code
 * pdfimages -list} is passed downstream explicitly either way.
 *
 * <p>The textual {@code pdfinfo}/{@code pdfimages -list} reports are parsed by the pure {@link
 * PdfListingParser}; this adapter only drives the external processes via {@link
 * ProcessRunner}/{@link Tasks}, resolving the binaries through {@link ToolPath}. The {@code
 * pdfimages}/{@code pdfinfo} override property keys are constructor parameters (e.g. {@code
 * despeckle.pdfimages.path}), so each app keeps its own. A missing binary or an unacceptable exit
 * surfaces as a plain {@link IOException}, leaving the policy to the app.
 */
public final class PdfImagesCliExtractor {

    private static final Duration INFO_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(10);

    private final String pdfimagesPropertyKey;
    private final String pdfinfoPropertyKey;

    /**
     * Create an extractor that shells out to the {@code pdfimages}/{@code pdfinfo} tools.
     *
     * @param pdfimagesPropertyKey the {@code -D} override the app uses to point at its bundled
     *     {@code pdfimages} (e.g. {@code despeckle.pdfimages.path})
     * @param pdfinfoPropertyKey the {@code -D} override the app uses to point at its bundled {@code
     *     pdfinfo} (e.g. {@code despeckle.pdfinfo.path})
     */
    public PdfImagesCliExtractor(String pdfimagesPropertyKey, String pdfinfoPropertyKey) {
        this.pdfimagesPropertyKey = pdfimagesPropertyKey;
        this.pdfinfoPropertyKey = pdfinfoPropertyKey;
    }

    /**
     * Resolve a required tool via {@link ToolPath}; a missing binary is a plain {@link
     * IOException}.
     */
    private static String resolve(String tool, String propertyKey) throws IOException {
        return ToolPath.resolve(tool, propertyKey)
                .map(Path::toString)
                .orElseThrow(
                        () ->
                                new IOException(
                                        tool
                                                + " not found on PATH; install it or set -D"
                                                + propertyKey
                                                + "=/path/to/"
                                                + tool));
    }

    /** Run a text-producing command, returning its captured stdout. */
    private static String capture(List<String> command, Duration timeout) throws IOException {
        try {
            return ProcessRunner.run(command, timeout).stdout();
        } catch (TimeoutException e) {
            throw new IOException(command.get(0) + " timed out after " + timeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(command.get(0) + " was interrupted", e);
        }
    }

    /** The page count of {@code pdf}, via {@code pdfinfo}. */
    private int pageCount(Path pdf) throws IOException {
        String pdfinfo = resolve("pdfinfo", pdfinfoPropertyKey);
        return PdfListingParser.parsePageCount(
                capture(List.of(pdfinfo, pdf.toString()), INFO_TIMEOUT));
    }

    /** The dominant x-ppi across the PDF's images, via {@code pdfimages -list}. */
    public int dominantDpi(Path pdf) throws IOException {
        String pdfimages = resolve("pdfimages", pdfimagesPropertyKey);
        return PdfListingParser.parseDominantDpi(
                capture(List.of(pdfimages, "-list", pdf.toString()), INFO_TIMEOUT));
    }

    /**
     * Extract all pages of {@code pdf} into {@code outDir} as TIFFs, parallelized over page-range
     * chunks of about {@link #CHUNK_PAGES} pages on {@code pool} (at most {@code 4 * jobs} chunks).
     *
     * <p>One {@code pdfimages -list} pass picks the mode: when every embedded image is 1-bpp CCITT
     * (the usual self-scanned book), each chunk dumps the raw G4 streams ({@code -ccitt}) and wraps
     * them into single-strip CCITT-G4 TIFFs — a pure remux: no decode/re-encode, intermediates tens
     * of KB per page instead of the decoded megabytes, and the image's true ppi stamped instead of
     * {@code pdfimages}' default 72 dpi. Every wrapped page is decoded back once as verification
     * (PDF's {@code EncodedByteAlign} never reaches the dumped params, so trust requires a decode);
     * a chunk whose dump or wrap deviates in any way is re-extracted decoded ({@code -tiff}), which
     * is also the whole-run mode for any other source.
     */
    public void extract(Path pdf, Path outDir, int jobs, ExecutorService pool) throws IOException {
        int total = pageCount(pdf);
        String pdfimages = resolve("pdfimages", pdfimagesPropertyKey);
        List<PdfListingParser.ImageRow> rows =
                PdfListingParser.parseImageRows(
                        capture(List.of(pdfimages, "-list", pdf.toString()), INFO_TIMEOUT));
        boolean rawCcitt =
                !rows.isEmpty()
                        && rows.stream().allMatch(r -> r.bpc() == 1 && "ccitt".equals(r.enc()));

        // Chunks of ~CHUNK_PAGES rather than total/jobs: fast finishers free their pool slot early
        // (the straggler tail shrinks from total/jobs to ~CHUNK_PAGES pages), and a streaming
        // consumer can take pages chunk by chunk. Capped so a small book is not all process spawns.
        int chunkCap = (int) Math.min(4L * jobs, total);
        int chunks = Math.clamp(Math.ceilDiv(total, CHUNK_PAGES), 1, Math.max(1, chunkCap));
        int per = Math.ceilDiv(total, chunks);
        List<Callable<Void>> tasks = new ArrayList<>();
        int chunk = 0;
        for (int first = 1; first <= total; first += per) {
            int last = Math.min(first + per - 1, total);
            String prefix =
                    outDir.resolve(String.format(Locale.ROOT, "page-c%03d-", chunk)).toString();
            int from = first;
            int to = last;
            List<PdfListingParser.ImageRow> chunkRows =
                    rawCcitt ? rowsInRange(rows, from, to) : List.of();
            tasks.add(
                    () -> {
                        extractChunk(pdfimages, pdf, from, to, prefix, chunkRows);
                        return null;
                    });
            chunk++;
        }
        Tasks.awaitAll(pool, tasks, "pdfimages extract interrupted", "pdfimages extract failed");
    }

    /** Pages per extraction chunk; see {@link #extract}. */
    private static final int CHUNK_PAGES = 12;

    /** The listing rows for pages {@code from..to}, in listing (= dump) order. */
    private static List<PdfListingParser.ImageRow> rowsInRange(
            List<PdfListingParser.ImageRow> rows, int from, int to) {
        return rows.stream().filter(r -> r.page() >= from && r.page() <= to).toList();
    }

    /**
     * Extract one page-range chunk: raw-CCITT remux when {@code ccittRows} describes it, decoded
     * {@code -tiff} otherwise — and the {@code -tiff} rerun as the fallback when the dump deviates
     * from the listing in any way (count, params shape, or a wrap that does not decode back).
     */
    private void extractChunk(
            String pdfimages,
            Path pdf,
            int from,
            int to,
            String prefix,
            List<PdfListingParser.ImageRow> ccittRows)
            throws IOException {
        if (ccittRows.isEmpty()) {
            runDiscarding(extractCommand(pdfimages, "-tiff", from, to, pdf, prefix));
            return;
        }
        runDiscarding(extractCommand(pdfimages, "-ccitt", from, to, pdf, prefix));
        if (!wrapChunk(prefix, ccittRows)) {
            deleteByPrefix(prefix);
            runDiscarding(extractCommand(pdfimages, "-tiff", from, to, pdf, prefix));
        }
    }

    private static List<String> extractCommand(
            String pdfimages, String format, int from, int to, Path pdf, String prefix) {
        return List.of(
                pdfimages,
                format,
                "-f",
                Integer.toString(from),
                "-l",
                Integer.toString(to),
                pdf.toString(),
                prefix);
    }

    /**
     * Wrap every {@code .ccitt} dump under {@code prefix} into a single-strip G4 TIFF, verifying
     * each by decoding it back. Returns {@code false} (without cleaning up) on any deviation; the
     * caller then discards the chunk's artifacts and falls back to a decoded extract.
     */
    private static boolean wrapChunk(String prefix, List<PdfListingParser.ImageRow> rows)
            throws IOException {
        List<Path> dumps = filesByPrefix(prefix, ".ccitt");
        if (dumps.size() != rows.size()) {
            return false;
        }
        for (int i = 0; i < dumps.size(); i++) {
            Path ccitt = dumps.get(i);
            PdfListingParser.ImageRow row = rows.get(i);
            Path paramsFile = withExtension(ccitt, ".params");
            if (!Files.isRegularFile(paramsFile)) {
                return false;
            }
            CcittTiffs.@Nullable Params params =
                    CcittTiffs.parseParams(Files.readString(paramsFile, StandardCharsets.UTF_8));
            if (params == null || !CcittTiffs.supported(params, row.width())) {
                return false;
            }
            Path out = withExtension(ccitt, ".tif");
            CcittTiffs.writeSingleStripG4(
                    out,
                    Files.readAllBytes(ccitt),
                    row.width(),
                    row.height(),
                    params.blackIs1(),
                    Math.max(row.xPpi(), 0));
            if (!decodesBack(out, row)) {
                return false;
            }
            Files.delete(ccitt);
            Files.delete(paramsFile);
        }
        return true;
    }

    /**
     * Whether the wrapped TIFF decodes to the listing row's dimensions — the read-back proof that
     * the stream really was plain T.6 (an {@code EncodedByteAlign} stream, undetectable from the
     * params file, fails to decode or comes back the wrong size here).
     */
    private static boolean decodesBack(Path tif, PdfListingParser.ImageRow row) {
        try (Pix pix = Pix.read(tif)) {
            return pix.width() == row.width() && pix.height() == row.height();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** The files starting with {@code prefix}'s file name and ending in {@code suffix}, sorted. */
    private static List<Path> filesByPrefix(String prefix, String suffix) throws IOException {
        Path prefixPath = Path.of(prefix);
        Path dir = prefixPath.getParent();
        String name = String.valueOf(prefixPath.getFileName());
        if (dir == null) {
            throw new IOException("extract prefix has no parent directory: " + prefix);
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(
                            p -> {
                                String fileName = String.valueOf(p.getFileName());
                                return fileName.startsWith(name) && fileName.endsWith(suffix);
                            })
                    .sorted(Comparator.comparing(p -> String.valueOf(p.getFileName())))
                    .toList();
        }
    }

    /**
     * Delete every artifact of one chunk ({@code .ccitt}, {@code .params}, partial {@code .tif}).
     */
    private static void deleteByPrefix(String prefix) throws IOException {
        for (String suffix : List.of(".ccitt", ".params", ".tif")) {
            for (Path file : filesByPrefix(prefix, suffix)) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** A sibling of {@code file} with its extension replaced by {@code extension}. */
    private static Path withExtension(Path file, String extension) {
        String name = String.valueOf(file.getFileName());
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return file.resolveSibling(base + extension);
    }

    /** Run an extraction command, discarding its (file-producing) output. */
    private static void runDiscarding(List<String> command) throws IOException {
        try {
            ProcessRunner.run(command, EXTRACT_TIMEOUT);
        } catch (TimeoutException e) {
            throw new IOException(command.get(0) + " timed out after " + EXTRACT_TIMEOUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(command.get(0) + " was interrupted", e);
        }
    }
}
