package io.github.p4suta.tateyokopdf.infrastructure.qpdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.domain.model.PdfOutputPolicy;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class QpdfLinearizerTest {

    @Test
    void noOpFallbackProcessesWithoutError(@TempDir Path tmp) throws Exception {
        Path pdf = Files.writeString(tmp.resolve("dummy.pdf"), "not really a pdf");
        PdfPostProcessor noOp = PdfPostProcessor.noOp();
        noOp.process(pdf);
        assertThat(Files.readString(pdf)).isEqualTo("not really a pdf");
    }

    @Test
    void resolveOnPathReturnsEmptyForEmptyPathEntries() {
        // Smoke check that the resolver does not blow up on absurd PATH shapes; the real
        // happy path is covered by the @EnabledIf integration test below.
        assertThat(QpdfLinearizer.resolveOnPath()).isNotNull();
    }

    @Test
    @EnabledIf("io.github.p4suta.tateyokopdf.infrastructure.qpdf.QpdfLinearizerTest#qpdfOnPath")
    void linearizesARealPdfWhenQpdfIsOnPath(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("sample.pdf");
        Files.write(pdf, minimalPdf());
        PdfPostProcessor processor = QpdfLinearizer.create();
        processor.process(pdf);

        // (1) Linearization dict lives at the head of the file, before any object stream, so the
        //     substring scan survives --object-streams=generate.
        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("/Linearized");

        // (2) Header byte must be lifted to the target version by --min-version. The input fixture
        //     starts at %PDF-1.4; qpdf rewrites it.
        byte[] head = Files.readAllBytes(pdf);
        String header = new String(head, 0, Math.min(8, head.length), StandardCharsets.ISO_8859_1);
        assertThat(header).startsWith("%PDF-" + PdfOutputPolicy.TARGET.label());

        // (3) Catalog /Version is the second source of truth — readers honor the higher of the
        // two.
        try (var doc = Loader.loadPDF(pdf.toFile())) {
            assertThat(doc.getVersion()).isEqualTo(PdfOutputPolicy.TARGET.headerValue());
        }

        // (4) No backup/temp litter left next to the output — regression guard for the
        //     --replace-input ".~qpdf-orig" leftover that surfaced in batch output directories.
        try (var entries = Files.list(tmp)) {
            assertThat(entries).containsExactly(pdf);
        }
    }

    @Test
    void processOnMissingFileFails(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.pdf");
        // /bin/false is irrelevant here — process() short-circuits before invoking the binary.
        PdfPostProcessor processor = new QpdfLinearizer(Path.of("/bin/false"));
        assertThatThrownBy(() -> processor.process(missing))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_WRITE_FAILED));
    }

    @Test
    @EnabledIf(
            "io.github.p4suta.tateyokopdf.infrastructure.qpdf.QpdfLinearizerTest#binFalseAvailable")
    void processFailsWhenBinaryExitsNonZero(@TempDir Path tmp) throws Exception {
        // /bin/false exits with code 1, which is neither 0 (success) nor 3 (warnings — accepted).
        // Drives the `code != 0 && code != 3` branch in process().
        Path pdf = Files.writeString(tmp.resolve("x.pdf"), "stub");
        PdfPostProcessor processor = new QpdfLinearizer(Path.of("/bin/false"));
        assertThatThrownBy(() -> processor.process(pdf))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_WRITE_FAILED));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void resolveBundledQpdfPrefersBinSubdirectory(@TempDir Path tmp) throws Exception {
        // Mirrors the upstream zip layout staged by `stageJpackageInput`:
        //   <jarDir>/bin/qpdf   (executable)
        Path binDir = Files.createDirectory(tmp.resolve("bin"));
        Path bundled = binDir.resolve("qpdf");
        Files.copy(Path.of("/bin/true"), bundled);
        Files.setPosixFilePermissions(bundled, PosixFilePermissions.fromString("rwxr-xr-x"));

        assertThat(QpdfLinearizer.resolveBundledQpdfIn(tmp)).contains(bundled);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void resolveBundledQpdfFallsBackToFlatLayout(@TempDir Path tmp) throws Exception {
        // Dev tree / older bundles may stage qpdf directly next to the jar.
        Path bundled = tmp.resolve("qpdf");
        Files.copy(Path.of("/bin/true"), bundled);
        Files.setPosixFilePermissions(bundled, PosixFilePermissions.fromString("rwxr-xr-x"));

        assertThat(QpdfLinearizer.resolveBundledQpdfIn(tmp)).contains(bundled);
    }

    @Test
    void resolveBundledQpdfReturnsEmptyWhenAbsent(@TempDir Path tmp) {
        assertThat(QpdfLinearizer.resolveBundledQpdfIn(tmp)).isEmpty();
    }

    @Test
    void processFailsWhenBinaryDoesNotExist(@TempDir Path tmp) throws Exception {
        // Non-existent binary path — ProcessBuilder.start() throws IOException, which the
        // adapter wraps as PDF_WRITE_FAILED. Drives the IOException catch branch.
        Path pdf = Files.writeString(tmp.resolve("x.pdf"), "stub");
        PdfPostProcessor processor =
                new QpdfLinearizer(tmp.resolve("definitely-not-a-real-binary"));
        assertThatThrownBy(() -> processor.process(pdf))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_WRITE_FAILED));
    }

    static boolean binFalseAvailable() {
        return Files.isExecutable(Path.of("/bin/false"));
    }

    static boolean qpdfOnPath() {
        return QpdfLinearizer.resolveOnPath().isPresent();
    }

    // The smallest PDF qpdf accepts as input — taken from the PDF 1.4 spec example.
    private static byte[] minimalPdf() {
        String body =
                """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj
                << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj
                << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << >> >>
                endobj
                4 0 obj
                << /Length 8 >>
                stream
                BT ET
                endstream
                endobj
                xref
                0 5
                0000000000 65535 f
                0000000009 00000 n
                0000000058 00000 n
                0000000115 00000 n
                0000000212 00000 n
                trailer
                << /Size 5 /Root 1 0 R >>
                startxref
                279
                %%EOF
                """;
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }
}
