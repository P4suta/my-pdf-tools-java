package io.github.p4suta.shared.pdf;

import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.process.Tasks;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.jspecify.annotations.Nullable;

/**
 * Packs a directory of cleaned bitonal pages into a lossless-JBIG2 PDF — the Java port of {@code
 * jbig2-pdf.py} + {@code pdfmeta.py}. Each page is encoded by {@code jbig2 -p} (jbig2enc's
 * generic-region mode, lossless; never the lossy {@code -s} symbol mode) in parallel, then embedded
 * verbatim as a {@code /JBIG2Decode} image XObject via PDFBox. Because the per-page JBIG2 streams
 * come from the same {@code jbig2} binary the Python pipeline used, the decoded pages are
 * bit-identical; the container is finished with a {@code qpdf --linearize} pass (the caller's, via
 * {@link QpdfRunner}) to keep the Fast-Web-View output the Python path produced.
 *
 * <p>Each page is sized by its own resolution (so a mixed-resolution book — e.g. a 600-dpi text
 * with a 1200-dpi plate — comes out correctly), unless the caller forces one DPI; a page that
 * carries no resolution falls back to {@link #DEFAULT_DPI}. This matches {@code jbig2-pdf.py}'s
 * per-image {@code page_dpi}.
 *
 * <p>The {@code jbig2} binary is resolved through the shared {@link ToolPath} island: an explicit
 * {@code -D<jbig2PropertyKey>} override wins (how a packaged app-image points at its bundled
 * binary), else the first {@code jbig2} on {@code PATH}. The property key is a constructor
 * PARAMETER, never a unified literal, so each app keeps its own ({@code register.jbig2.path},
 * {@code despeckle.jbig2.path}); a missing binary surfaces as a plain {@link IOException} — the
 * "fatal vs skip" policy stays with the app.
 *
 * <p>The {@code jbig2 -p} encode is driven by a single LOCAL {@link ProcessBuilder} rather than the
 * shared {@code ProcessRunner}: {@code jbig2 -p} writes a RAW binary JBIG2 stream to stdout, and
 * the shared runner decodes captured stdout to a UTF-8 {@link String}, which would corrupt the
 * binary bytes. Redirecting stdout straight to the per-page scratch file is binary-safe by
 * construction.
 */
public final class PdfBoxJbig2Assembler {

    /** Page size assumed when an image carries no resolution (matches {@code jbig2-pdf.py}). */
    static final int DEFAULT_DPI = 300;

    private static final COSName JBIG2_DECODE = COSName.getPDFName("JBIG2Decode");
    private static final long ENCODE_TIMEOUT_SECONDS = 300;

    private final String jbig2PropertyKey;

    /**
     * Create an assembler that encodes pages with {@code jbig2} and writes the PDF via PDFBox.
     *
     * @param jbig2PropertyKey the {@code -D} system-property override the app uses to point at its
     *     bundled {@code jbig2} binary (e.g. {@code despeckle.jbig2.path}); kept a parameter so
     *     each app passes its own and packaged app-image runs keep resolving
     */
    public PdfBoxJbig2Assembler(String jbig2PropertyKey) {
        this.jbig2PropertyKey = jbig2PropertyKey;
    }

    /** A page ready to embed: its lossless JBIG2 stream (on disk), pixel size and its own DPI. */
    private record Page(Path jbig2, int width, int height, int dpi) {}

    /** The pixel size and resolution of a cleaned page image. */
    private record ImageInfo(int width, int height, int resolution) {}

    /**
     * Assemble {@code imageDir}'s cleaned pages into {@code outPdf}.
     *
     * @param imageDir the directory of cleaned bitonal pages (name order is reading order)
     * @param outPdf the lossless-JBIG2 PDF to write
     * @param source a PDF whose Info dict, XMP and version are inherited, or {@code null} for none
     * @param forcedDpi a single DPI to size every page with, or empty to read each image's own
     * @param pool the worker pool the per-page {@code jbig2} encodes run on
     * @param scratchDir scratch directory for the intermediate per-page JBIG2 streams
     *     (caller-owned)
     * @throws IOException if the directory is empty, a tool fails, or the write fails
     */
    public void assemble(
            Path imageDir,
            Path outPdf,
            @Nullable Path source,
            OptionalInt forcedDpi,
            ExecutorService pool,
            Path scratchDir)
            throws IOException {
        List<Path> images = sortedImages(imageDir);
        if (images.isEmpty()) {
            throw new IOException("no cleaned images to pack in " + imageDir);
        }
        String jbig2 = jbig2();
        List<Callable<Page>> tasks = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            Path image = images.get(i);
            int index = i;
            tasks.add(() -> encode(jbig2, image, scratchDir, index, forcedDpi));
        }
        List<Page> pages =
                Tasks.awaitAll(pool, tasks, "jbig2 encode interrupted", "jbig2 encode failed");

        try (PDDocument doc = new PDDocument()) {
            for (Page page : pages) {
                addPage(doc, page);
            }
            inheritMetadata(doc, source);
            doc.save(outPdf.toFile());
        }
    }

    /**
     * Resolve the {@code jbig2} binary via the shared {@link ToolPath} island. A missing binary is
     * a plain {@link IOException} here — the encode cannot proceed — leaving the "fatal vs skip"
     * policy to the app, which sees the IOException at the {@link #assemble} boundary.
     */
    private String jbig2() throws IOException {
        return ToolPath.resolve("jbig2", jbig2PropertyKey)
                .map(Path::toString)
                .orElseThrow(
                        () ->
                                new IOException(
                                        "jbig2 not found on PATH; install it or set -D"
                                                + jbig2PropertyKey
                                                + "=/path/to/jbig2"));
    }

    /** Encode one page to a lossless JBIG2 stream on disk; safe to run in parallel. */
    private static Page encode(
            String jbig2, Path image, Path jb2Dir, int index, OptionalInt forcedDpi)
            throws IOException {
        ImageInfo info = readImageInfo(image);
        int dpi = forcedDpi.orElse(info.resolution() > 0 ? info.resolution() : DEFAULT_DPI);
        Path out = jb2Dir.resolve(String.format(Locale.ROOT, "%06d.jb2", index));
        // jbig2 -p writes the RAW binary JBIG2 stream to stdout. Redirect it straight to the
        // scratch
        // file: the shared ProcessRunner decodes stdout to a UTF-8 String, which would corrupt
        // these
        // binary bytes, so this one call site keeps a local ProcessBuilder (binary-safe).
        ProcessBuilder pb = new ProcessBuilder(jbig2, "-p", image.toString());
        pb.redirectOutput(out.toFile());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        try {
            if (!process.waitFor(ENCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(
                        "jbig2 timed out after " + ENCODE_TIMEOUT_SECONDS + "s on " + image);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("jbig2 was interrupted on " + image, e);
        }
        int code = process.exitValue();
        if (code != 0) {
            throw new IOException("jbig2 failed with exit code " + code + " on " + image);
        }
        return new Page(out, info.width(), info.height(), dpi);
    }

    /**
     * Read a cleaned page's pixel size and resolution via Leptonica (the shared {@link Pix}). The
     * read goes through Leptonica — not {@code javax.imageio} — because the cleaned pages are
     * bitonal raw PBM (P4) and Group-4 TIFF, which Leptonica decodes natively but ImageIO has no
     * reader for. {@link Pix#resolution()} returns {@code 0} when the format carries no DPI tag
     * (PBM never does), so the caller's {@link #DEFAULT_DPI} fallback (or {@code forcedDpi}) wins —
     * exactly the per-image {@code page_dpi} behavior of {@code jbig2-pdf.py}.
     */
    private static ImageInfo readImageInfo(Path image) {
        try (Pix pix = Pix.read(image)) {
            return new ImageInfo(pix.width(), pix.height(), pix.resolution());
        }
    }

    /** Embed one page's JBIG2 stream as a full-page {@code /JBIG2Decode} image XObject. */
    private static void addPage(PDDocument doc, Page page) throws IOException {
        COSStream cos = doc.getDocument().createCOSStream();
        // createRawOutputStream stores the bytes verbatim (no re-filtering) — the analogue of
        // pikepdf's Stream(pdf, data); createOutputStream(JBIG2Decode) would try to *encode*, which
        // PDFBox cannot do for JBIG2.
        try (OutputStream raw = cos.createRawOutputStream();
                InputStream in = Files.newInputStream(page.jbig2())) {
            in.transferTo(raw);
        }
        cos.setItem(COSName.TYPE, COSName.XOBJECT);
        cos.setItem(COSName.SUBTYPE, COSName.IMAGE);
        cos.setInt(COSName.WIDTH, page.width());
        cos.setInt(COSName.HEIGHT, page.height());
        cos.setItem(COSName.COLORSPACE, COSName.DEVICEGRAY);
        cos.setInt(COSName.BITS_PER_COMPONENT, 1);
        cos.setItem(COSName.FILTER, JBIG2_DECODE);
        PDImageXObject image = new PDImageXObject(new PDStream(cos), null);

        float widthPt = points(page.width(), page.dpi());
        float heightPt = points(page.height(), page.dpi());
        PDPage pdPage = new PDPage(new PDRectangle(widthPt, heightPt));
        doc.addPage(pdPage);
        try (PDPageContentStream content = new PDPageContentStream(doc, pdPage)) {
            content.drawImage(image, 0, 0, widthPt, heightPt);
        }
    }

    /** Copy the source PDF's Info dict, XMP metadata and (&ge; 1.4) version onto the output. */
    private static void inheritMetadata(PDDocument doc, @Nullable Path source) throws IOException {
        if (source == null) {
            // No source scan to mirror (the topdf path may pack loose pages): keep PDFBox's own
            // Info, but still never declare a version below 1.4, which JBIG2Decode requires.
            doc.setVersion(Math.max(doc.getVersion(), 1.4f));
            return;
        }
        try (PDDocument src = Loader.loadPDF(source.toFile())) {
            COSDictionary srcInfo = src.getDocumentInformation().getCOSObject();
            COSDictionary outInfo = doc.getDocumentInformation().getCOSObject();
            for (COSName key : srcInfo.keySet()) {
                outInfo.setItem(key, srcInfo.getItem(key));
            }
            PDMetadata srcMetadata = src.getDocumentCatalog().getMetadata();
            if (srcMetadata != null) {
                byte[] xmp;
                try (InputStream in = srcMetadata.createInputStream()) {
                    xmp = in.readAllBytes();
                }
                PDMetadata outMetadata = new PDMetadata(doc);
                outMetadata.importXMPMetadata(xmp);
                doc.getDocumentCatalog().setMetadata(outMetadata);
            }
            // JBIG2Decode is a PDF 1.4 feature, so never declare a version below 1.4.
            doc.setVersion(Math.max(src.getVersion(), 1.4f));
        }
    }

    /** Page extent in points: pixels / dpi * 72, rounded to 4 dp (matches {@code jbig2-pdf.py}). */
    private static float points(int pixels, int dpi) {
        double pt = (double) pixels / dpi * 72.0;
        return (float) (Math.round(pt * 10_000.0) / 10_000.0);
    }

    private static List<Path> sortedImages(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
