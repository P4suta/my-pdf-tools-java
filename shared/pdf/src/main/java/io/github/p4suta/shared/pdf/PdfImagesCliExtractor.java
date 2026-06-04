package io.github.p4suta.shared.pdf;

import io.github.p4suta.shared.process.ProcessRunner;
import io.github.p4suta.shared.process.Tasks;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Extracts a PDF's embedded bitonal images as TIFFs by driving {@code pdfimages} — the Java port of
 * the {@code just extract} (pdfimages) + {@code stamp-dpi.py --print} glue. The page range is split
 * across the worker pool (one {@code pdfimages -f/-l} per chunk) with distinct zero-padded {@code
 * page-cNN-} prefixes, so a name sort yields reading order and chunks never collide. The dominant
 * scan DPI is read from {@code pdfimages -list} and passed to the clean step as an explicit DPI, so
 * the extracted TIFFs (which {@code pdfimages} tags at a default 72 dpi) never need re-tagging.
 *
 * <p>The textual {@code pdfinfo}/{@code pdfimages -list} reports are parsed by the pure {@link
 * PdfListingParser}; this adapter only drives the external processes via the shared {@link
 * ProcessRunner}/{@link Tasks}, resolving the binaries through {@link ToolPath}. The {@code
 * pdfimages}/{@code pdfinfo} override property keys are constructor PARAMETERS (e.g. {@code
 * despeckle.pdfimages.path}), never unified literals, so each app keeps its own and packaged
 * app-image runs keep resolving. A missing binary or an unacceptable exit surfaces as a plain
 * {@link IOException} — the policy stays with the app.
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
     * chunks. {@code jobs} bounds both the chunk count and the pool slots used.
     */
    public void extract(Path pdf, Path outDir, int jobs, ExecutorService pool) throws IOException {
        int total = pageCount(pdf);
        int chunks = Math.max(1, Math.min(jobs, total));
        int per = (total + chunks - 1) / chunks;
        String pdfimages = resolve("pdfimages", pdfimagesPropertyKey);
        List<Callable<Void>> tasks = new ArrayList<>();
        int chunk = 0;
        for (int first = 1; first <= total; first += per) {
            int last = Math.min(first + per - 1, total);
            String prefix =
                    outDir.resolve(String.format(Locale.ROOT, "page-c%03d-", chunk)).toString();
            int from = first;
            int to = last;
            tasks.add(
                    () -> {
                        runDiscarding(
                                List.of(
                                        pdfimages,
                                        "-tiff",
                                        "-f",
                                        Integer.toString(from),
                                        "-l",
                                        Integer.toString(to),
                                        pdf.toString(),
                                        prefix));
                        return null;
                    });
            chunk++;
        }
        Tasks.awaitAll(pool, tasks, "pdfimages extract interrupted", "pdfimages extract failed");
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
