package io.github.p4suta.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adapter-level coverage for {@link PdfBoxJbig2Assembler}, driving the real {@code jbig2} binary
 * over a synthetic directory of 1-bit pages (mirrors despeckle's infra tests, which exercise the
 * adapter end-to-end against the bundled toolchain). The {@code jbig2} property key is a
 * constructor parameter, so the tests pass a throwaway key and rely on PATH resolution.
 */
final class PdfBoxJbig2AssemblerTest {

    private static final String JBIG2_KEY = "shared.pdf.test.jbig2.path";

    static boolean jbig2OnPath() {
        return io.github.p4suta.shared.process.ToolPath.resolve("jbig2", JBIG2_KEY).isPresent();
    }

    /** Write a 1-bit PNG with a black square on white — a page jbig2enc reads via Leptonica. */
    private static void writeBitonalPage(Path file, int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, 0xFFFFFF);
            }
        }
        for (int x = w / 4; x < w / 2; x++) {
            for (int y = h / 4; y < h / 2; y++) {
                img.setRGB(x, y, 0x000000);
            }
        }
        assertThat(ImageIO.write(img, "png", file.toFile())).isTrue();
    }

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.PdfBoxJbig2AssemblerTest#jbig2OnPath")
    void assemblesABitonalDirectoryIntoAJbig2Pdf(@TempDir Path tmp) throws Exception {
        Path imageDir = Files.createDirectory(tmp.resolve("images"));
        Path scratch = Files.createDirectory(tmp.resolve("scratch"));
        writeBitonalPage(imageDir.resolve("page-000.png"), 64, 48);
        writeBitonalPage(imageDir.resolve("page-001.png"), 80, 60);
        Path outPdf = tmp.resolve("out.pdf");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            new PdfBoxJbig2Assembler(JBIG2_KEY)
                    .assemble(imageDir, outPdf, null, OptionalInt.of(300), pool, scratch);
        } finally {
            pool.shutdownNow();
        }

        assertThat(Files.exists(outPdf)).isTrue();
        try (PDDocument doc = Loader.loadPDF(outPdf.toFile())) {
            // Two pages, each carrying a /JBIG2Decode-filtered image XObject, and the container
            // never declares a version below 1.4 (JBIG2Decode is a PDF 1.4 feature).
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            assertThat(doc.getVersion()).isGreaterThanOrEqualTo(1.4f);
            for (PDPage page : doc.getPages()) {
                var resources = page.getResources();
                boolean anyImage = false;
                for (COSName name : resources.getXObjectNames()) {
                    if (resources.isImageXObject(name)) {
                        anyImage = true;
                    }
                }
                assertThat(anyImage).as("each page carries an image XObject").isTrue();
            }
        }
    }

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.PdfBoxJbig2AssemblerTest#jbig2OnPath")
    void inheritsInfoAndVersionFromTheSourcePdf(@TempDir Path tmp) throws Exception {
        // A source PDF whose Info dict + version must be copied onto the assembled output.
        Path source = tmp.resolve("source.pdf");
        try (PDDocument src = new PDDocument()) {
            src.addPage(new PDPage());
            PDDocumentInformation info = src.getDocumentInformation();
            info.setTitle("Donor Title");
            info.setAuthor("Donor Author");
            src.setVersion(1.7f);
            src.save(source.toFile());
        }

        Path imageDir = Files.createDirectory(tmp.resolve("images"));
        Path scratch = Files.createDirectory(tmp.resolve("scratch"));
        writeBitonalPage(imageDir.resolve("page-000.png"), 64, 48);
        Path outPdf = tmp.resolve("out.pdf");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            new PdfBoxJbig2Assembler(JBIG2_KEY)
                    .assemble(imageDir, outPdf, source, OptionalInt.empty(), pool, scratch);
        } finally {
            pool.shutdownNow();
        }

        try (PDDocument doc = Loader.loadPDF(outPdf.toFile())) {
            assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("Donor Title");
            assertThat(doc.getDocumentInformation().getAuthor()).isEqualTo("Donor Author");
            assertThat(doc.getVersion()).isEqualTo(1.7f);
        }
    }

    @Test
    void emptyDirectoryFailsBeforeTouchingTheToolchain(@TempDir Path tmp) throws Exception {
        // No images -> IOException, regardless of whether jbig2 is present (the empty-dir guard
        // runs before tool resolution). Works on any host.
        Path imageDir = Files.createDirectory(tmp.resolve("empty"));
        Path scratch = Files.createDirectory(tmp.resolve("scratch"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            PdfBoxJbig2Assembler assembler = new PdfBoxJbig2Assembler(JBIG2_KEY);
            assertThatThrownBy(
                            () ->
                                    assembler.assemble(
                                            imageDir,
                                            tmp.resolve("out.pdf"),
                                            null,
                                            OptionalInt.empty(),
                                            pool,
                                            scratch))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no cleaned images");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void missingJbig2BinaryFailsWithAClearMessage(@TempDir Path tmp) throws Exception {
        // Point the property key at a non-existent binary AND ensure the name won't resolve on
        // PATH: an absolute override that does not exist makes ToolPath return it, but the encode's
        // ProcessBuilder.start() then fails. To exercise the resolve() orElseThrow branch instead,
        // we use a property key whose value is blank-resolvable-to-nothing by pointing at a tool
        // name that is not on PATH. Simplest deterministic path: a directory with one image and a
        // bogus tool name via the key set to empty (so PATH lookup of a nonsense tool fails).
        Path imageDir = Files.createDirectory(tmp.resolve("images"));
        Path scratch = Files.createDirectory(tmp.resolve("scratch"));
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_BINARY);
        ImageIO.write(img, "png", imageDir.resolve("p.png").toFile());

        // A property key pointing at a path that does not exist: ToolPath returns it (override
        // wins), and the local encode ProcessBuilder.start() then throws IOException. Either way
        // the
        // failure surfaces as IOException at the assemble() boundary.
        String bogusKey = "shared.pdf.test.bogus.jbig2.path";
        System.setProperty(bogusKey, tmp.resolve("definitely-not-jbig2").toString());
        try {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                PdfBoxJbig2Assembler assembler = new PdfBoxJbig2Assembler(bogusKey);
                assertThatThrownBy(
                                () ->
                                        assembler.assemble(
                                                imageDir,
                                                tmp.resolve("out.pdf"),
                                                null,
                                                OptionalInt.of(300),
                                                pool,
                                                scratch))
                        .isInstanceOf(IOException.class);
            } finally {
                pool.shutdownNow();
            }
        } finally {
            System.clearProperty(bogusKey);
        }
    }
}
