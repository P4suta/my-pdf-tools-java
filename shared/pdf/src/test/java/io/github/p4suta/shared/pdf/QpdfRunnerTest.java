package io.github.p4suta.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.shared.process.ProcessRunner;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage for {@link QpdfRunner}. The runner returns a {@link ProcessRunner.Result} and propagates
 * {@code IOException}/{@code TimeoutException}/{@code InterruptedException} without degrading or
 * wrapping as a domain exception (that policy is the app's), so the tests assert on the returned
 * Result and the propagated IOException.
 */
final class QpdfRunnerTest {

    private static final String QPDF_KEY = "shared.pdf.test.qpdf.path";

    static boolean qpdfOnPath() {
        return ToolPath.resolve("qpdf", QPDF_KEY).isPresent();
    }

    static boolean binFalseAvailable() {
        return Files.isExecutable(Path.of("/bin/false"));
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

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.QpdfRunnerTest#qpdfOnPath")
    void linearizeOnlyWritesAFastWebViewCopy(@TempDir Path tmp) throws Exception {
        Path in = tmp.resolve("in.pdf");
        Path out = tmp.resolve("out.pdf");
        Files.write(in, minimalPdf());

        QpdfRunner runner = QpdfRunner.linearizeOnly(QPDF_KEY);
        assertThat(runner.isAvailable()).isTrue();
        assertThat(runner.binaryPath()).isPresent();

        ProcessRunner.Result result = runner.linearize(in, out);
        // qpdf returns 0 on clean input; 3 (warnings) is also accepted by the runner.
        assertThat(result.exitCode()).isIn(0, 3);
        assertThat(Files.exists(out)).isTrue();

        String content = Files.readString(out, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("/Linearized");
    }

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.QpdfRunnerTest#qpdfOnPath")
    void modernizingBumpsTheHeaderVersion(@TempDir Path tmp) throws Exception {
        Path in = tmp.resolve("in.pdf");
        Path out = tmp.resolve("out.pdf");
        Files.write(in, minimalPdf());

        // --min-version=1.7 rewrites the %PDF-1.4 header byte; --newline-before-endstream adds the
        // PDF/A EOL marker. Both flags must be threaded into the command.
        QpdfRunner runner = QpdfRunner.modernizing(QPDF_KEY, "1.7", true);
        ProcessRunner.Result result = runner.linearize(in, out);
        assertThat(result.exitCode()).isIn(0, 3);

        byte[] head = Files.readAllBytes(out);
        String header = new String(head, 0, Math.min(8, head.length), StandardCharsets.ISO_8859_1);
        assertThat(header).startsWith("%PDF-1.7");
    }

    @Test
    @EnabledIf("io.github.p4suta.shared.pdf.QpdfRunnerTest#binFalseAvailable")
    void propagatesIoExceptionOnUnacceptableExit(@TempDir Path tmp) throws Exception {
        // /bin/false exits 1 — neither 0 nor the accepted 3 — so the shared runner throws
        // IOException, which QpdfRunner propagates unchanged (no degrade, no wrap).
        Path in = tmp.resolve("in.pdf");
        Path out = tmp.resolve("out.pdf");
        Files.writeString(in, "stub");
        String key = "shared.pdf.test.false.qpdf.path";
        System.setProperty(key, "/bin/false");
        try {
            QpdfRunner runner = QpdfRunner.linearizeOnly(key);
            assertThat(runner.isAvailable()).isTrue();
            assertThatThrownBy(() -> runner.linearize(in, out)).isInstanceOf(IOException.class);
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void unresolvedBinaryThrowsIoException(@TempDir Path tmp) throws Exception {
        // An override pointing at a non-existent path resolves (override wins) but fails to launch,
        // so start() throws IOException.
        Path in = Files.writeString(tmp.resolve("in.pdf"), "stub");
        Path out = tmp.resolve("out.pdf");
        String key = "shared.pdf.test.missing.qpdf.path";
        System.setProperty(key, tmp.resolve("definitely-not-qpdf").toString());
        try {
            QpdfRunner runner = QpdfRunner.linearizeOnly(key);
            assertThatThrownBy(() -> runner.linearize(in, out)).isInstanceOf(IOException.class);
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void withTimeoutReturnsAConfiguredCopy() {
        QpdfRunner base = QpdfRunner.linearizeOnly(QPDF_KEY);
        QpdfRunner shorter = base.withTimeout(java.time.Duration.ofSeconds(30));
        // A different instance, still pointing at the same (PATH-resolvable or not) binary key.
        assertThat(shorter).isNotSameAs(base);
        assertThat(shorter.isAvailable()).isEqualTo(base.isAvailable());
    }
}
