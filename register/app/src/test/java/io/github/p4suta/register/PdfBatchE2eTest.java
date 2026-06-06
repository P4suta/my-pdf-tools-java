package io.github.p4suta.register;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.p4suta.register.application.PdfBatchService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.RegisterOptions;
import io.github.p4suta.register.infrastructure.process.NativeTools;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end batch: a directory of synthetic scan PDFs goes through {@link PdfBatchService} to a
 * directory of lossless-JBIG2 PDFs. Gated on {@code pdfimages}/{@code jbig2}/{@code pdfinfo} being
 * installed (like {@link PdfPipelineE2eTest}), so a CI box without the tools skips rather than
 * fails.
 */
class PdfBatchE2eTest {

    private static RegisterOptions options() {
        return new RegisterOptions(OptionalInt.empty(), null, false, true, 0.5, Anchor.TOP_RIGHT);
    }

    @Test
    void registersEveryPdfInTheDirectory(@TempDir Path tmp) throws Exception {
        assumeTrue(toolsAvailable(), "pdfimages/jbig2/pdfinfo not installed");
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        makeScanPdf(in.resolve("a.pdf"), 2);
        makeScanPdf(in.resolve("b.pdf"), 3);

        PdfBatchService.Summary summary =
                TestComposition.pdfBatchService()
                        .run(new PdfBatchService.Config(in, out, options(), 2, false, ""));

        assertEquals(new PdfBatchService.Summary(2, 0, 0), summary);
        assertTrue(startsWithPdfMagic(out.resolve("a.pdf")), "a.pdf registered");
        assertTrue(startsWithPdfMagic(out.resolve("b.pdf")), "b.pdf registered");
    }

    @Test
    void skipsExistingOutputsUnlessForced(@TempDir Path tmp) throws Exception {
        assumeTrue(toolsAvailable(), "pdfimages/jbig2/pdfinfo not installed");
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = Files.createDirectories(tmp.resolve("out"));
        makeScanPdf(in.resolve("a.pdf"), 2);
        makeScanPdf(in.resolve("b.pdf"), 2);
        // a.pdf is "already done": a sentinel the batch must leave untouched without --force.
        Path existing = out.resolve("a.pdf");
        Files.writeString(existing, "SENTINEL");

        PdfBatchService.Summary summary =
                TestComposition.pdfBatchService()
                        .run(new PdfBatchService.Config(in, out, options(), 2, false, ""));

        assertEquals(new PdfBatchService.Summary(1, 1, 0), summary);
        assertEquals("SENTINEL", Files.readString(existing), "skipped output left untouched");
        assertTrue(startsWithPdfMagic(out.resolve("b.pdf")), "the unfinished book is registered");
    }

    @Test
    void continuesPastABadPdfAndCountsTheFailure(@TempDir Path tmp) throws Exception {
        assumeTrue(toolsAvailable(), "pdfimages/jbig2/pdfinfo not installed");
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        makeScanPdf(in.resolve("good.pdf"), 2);
        Files.writeString(
                in.resolve("bad.pdf"), "not a pdf"); // sorted first; must not abort the run

        PdfBatchService.Summary summary =
                TestComposition.pdfBatchService()
                        .run(new PdfBatchService.Config(in, out, options(), 2, false, ""));

        assertEquals(1, summary.ok(), "the healthy book still registered");
        assertEquals(1, summary.failed(), "the bad book is counted, not fatal");
        assertEquals(0, summary.skipped());
        assertTrue(startsWithPdfMagic(out.resolve("good.pdf")));
        assertFalse(Files.exists(out.resolve("bad.pdf")), "the failed book produced no output");
    }

    @Test
    void appliesTheOutputSuffix(@TempDir Path tmp) throws Exception {
        assumeTrue(toolsAvailable(), "pdfimages/jbig2/pdfinfo not installed");
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        makeScanPdf(in.resolve("book.pdf"), 2);

        PdfBatchService.Summary summary =
                TestComposition.pdfBatchService()
                        .run(
                                new PdfBatchService.Config(
                                        in, out, options(), 2, false, "_registered"));

        assertEquals(new PdfBatchService.Summary(1, 0, 0), summary);
        assertTrue(
                startsWithPdfMagic(out.resolve("book_registered.pdf")), "suffixed output written");
        assertFalse(Files.exists(out.resolve("book.pdf")), "no un-suffixed output");
    }

    // helpers (mirror PdfPipelineE2eTest's synthetic scan)

    private static void makeScanPdf(Path target, int pages) throws IOException {
        int width = 413; // ~A6 width at 100 dpi (105 mm)
        int height = 583; // ~A6 height at 100 dpi (148 mm)
        int dpi = 100;
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDImageXObject image =
                        LosslessFactory.createFromImage(doc, column(width, height, 120 + i));
                float widthPt = width / (float) dpi * 72f;
                float heightPt = height / (float) dpi * 72f;
                PDPage page = new PDPage(new PDRectangle(widthPt, heightPt));
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(image, 0, 0, widthPt, heightPt);
                }
            }
            doc.save(target.toFile());
        }
    }

    /** A white page with a single jittered black "text column". */
    private static BufferedImage column(int width, int height, int columnX) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.fillRect(columnX, 40, 180, height - 80);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static boolean startsWithPdfMagic(Path pdf) throws IOException {
        try (InputStream in = Files.newInputStream(pdf)) {
            byte[] magic = in.readNBytes(4);
            return magic.length == 4
                    && magic[0] == '%'
                    && magic[1] == 'P'
                    && magic[2] == 'D'
                    && magic[3] == 'F';
        }
    }

    private static boolean toolsAvailable() {
        try {
            NativeTools.pdfinfo();
            NativeTools.pdfimages();
            NativeTools.jbig2();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
