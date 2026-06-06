package io.github.p4suta.tateyokopdf.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.p4suta.tateyokopdf.testfixtures.PdfFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLocks({
    @ResourceLock(value = Resources.SYSTEM_OUT, mode = ResourceAccessMode.READ_WRITE),
    @ResourceLock(value = Resources.SYSTEM_ERR, mode = ResourceAccessMode.READ_WRITE)
})
final class SpreadCommandTest {

    @Test
    void helpReturnsZero() {
        Captured c = run("--help");
        assertThat(c.code()).isZero();
        assertThat(c.outText()).contains("Usage");
    }

    @Test
    void versionReturnsZero() {
        Captured c = run("--version");
        assertThat(c.code()).isZero();
        assertThat(c.outText()).contains("tate-yoko-pdf 0.1.0");
    }

    @Test
    void noArgsPrintsHelpAndReturnsZero() {
        Captured c = run();
        assertThat(c.code()).isZero();
        assertThat(c.outText()).contains("Usage");
    }

    @Test
    void validPdfConvertsSuccessfully(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 4);
        Path output = tmp.resolve("out.pdf");
        Captured c = run(input.toString(), "-o", output.toString());
        assertThat(c.code()).isZero();
        assertThat(Files.exists(output)).isTrue();
        assertThat(Files.size(output)).isPositive();
    }

    @Test
    void brokenPdfReturnsNonZero(@TempDir Path tmp) throws Exception {
        Path garbage = PdfFixtures.corruptedHeader(tmp, "bad.pdf");
        Captured c = run(garbage.toString(), "-o", tmp.resolve("out.pdf").toString());
        assertThat(c.code()).isNotZero();
    }

    @Test
    void missingInputReturnsInputNotFound(@TempDir Path tmp) {
        Captured c = run(tmp.resolve("missing.pdf").toString());
        assertThat(c.code()).isEqualTo(CliExitCodes.INPUT_NOTFOUND);
    }

    @Test
    void firstPageLeftAccepted(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 5);
        Path output = tmp.resolve("out.pdf");
        Captured c = run(input.toString(), "-o", output.toString(), "--first-page", "left");
        assertThat(c.code()).isZero();
        assertThat(Files.exists(output)).isTrue();
    }

    @Test
    void firstPageRightAccepted(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 5);
        Path output = tmp.resolve("out.pdf");
        Captured c = run(input.toString(), "-o", output.toString(), "--first-page", "right");
        assertThat(c.code()).isZero();
        assertThat(Files.exists(output)).isTrue();
    }

    @Test
    void firstPageCoverAccepted(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 5);
        Path output = tmp.resolve("out.pdf");
        Captured c = run(input.toString(), "-o", output.toString(), "--first-page", "cover");
        assertThat(c.code()).isZero();
        assertThat(Files.exists(output)).isTrue();
    }

    @Test
    void unknownFirstPageRejectedWithUsageCode(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
        Captured c = run(input.toString(), "--first-page", "sideways");
        assertThat(c.code()).isEqualTo(CliExitCodes.USAGE);
    }

    @Test
    void pdfAFlagAccepted(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.blankPages(tmp, "in.pdf", 2);
        Path output = tmp.resolve("out.pdf");
        Captured c = run(input.toString(), "-o", output.toString(), "--pdf-a");
        assertThat(c.code()).isZero();
        assertThat(Files.exists(output)).isTrue();
    }

    @Test
    void lowMemoryFlagAccepted(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 4);
        Path output = tmp.resolve("out.pdf");
        // --low-memory routes the conversion through the temp-file stream cache; it must
        // still succeed and produce a non-empty PDF.
        Captured c = run(input.toString(), "-o", output.toString(), "--low-memory");
        assertThat(c.code()).isZero();
        assertThat(Files.exists(output)).isTrue();
        assertThat(Files.size(output)).isPositive();
    }

    @Test
    void ltrDirectionAccepted(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
        Path output = tmp.resolve("out.pdf");
        Captured c = run(input.toString(), "-o", output.toString(), "-d", "LTR");
        assertThat(c.code()).isZero();
    }

    @Test
    void unknownDirectionRejectedWithUsageCode(@TempDir Path tmp) throws Exception {
        Path input = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
        Captured c = run(input.toString(), "-d", "WTF");
        assertThat(c.code()).isEqualTo(CliExitCodes.USAGE);
    }

    @Test
    void multipleFilesProduceSiblingOutputs(@TempDir Path tmp) throws Exception {
        Path a = PdfFixtures.multiPageA4(tmp, "a.pdf", 2);
        Path b = PdfFixtures.multiPageA4(tmp, "b.pdf", 2);
        Captured c = run(a.toString(), b.toString());
        assertThat(c.code()).isZero();
        assertThat(Files.exists(tmp.resolve("a_spread.pdf"))).isTrue();
        assertThat(Files.exists(tmp.resolve("b_spread.pdf"))).isTrue();
    }

    @Test
    void directoryInputBatchesIntoOutputDir(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        PdfFixtures.multiPageA4(in, "a.pdf", 2);
        PdfFixtures.multiPageA4(in, "b.pdf", 2);
        Path out = tmp.resolve("out");
        Captured c = run(in.toString(), "-o", out.toString());
        assertThat(c.code()).isZero();
        assertThat(Files.exists(out.resolve("a_spread.pdf"))).isTrue();
        assertThat(Files.exists(out.resolve("b_spread.pdf"))).isTrue();
    }

    @Test
    void batchContinuesOnErrorAndReturnsNonZero(@TempDir Path tmp) throws Exception {
        Path good = PdfFixtures.multiPageA4(tmp, "good.pdf", 2);
        Path bad = PdfFixtures.corruptedHeader(tmp, "bad.pdf");
        Path out = tmp.resolve("out");
        Captured c = run(good.toString(), bad.toString(), "-o", out.toString());
        assertThat(c.code()).isNotZero();
        assertThat(Files.exists(out.resolve("good_spread.pdf"))).isTrue();
        assertThat(c.err()).contains("PDF_CORRUPTED");
    }

    @Test
    void stdinToStdoutProducesPdfBytes(@TempDir Path tmp) throws Exception {
        Path src = PdfFixtures.multiPageA4(tmp, "in.pdf", 4);
        byte[] pdf = Files.readAllBytes(src);
        Captured c = run(pdf, "-", "-o", "-");
        assertThat(c.code()).isZero();
        assertThat(c.out()).hasSizeGreaterThan(4);
        assertThat(new String(c.out(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void stdinCombinedWithOtherInputsIsUsageError(@TempDir Path tmp) throws Exception {
        Path src = PdfFixtures.multiPageA4(tmp, "in.pdf", 2);
        Captured c = run("-", src.toString());
        assertThat(c.code()).isEqualTo(CliExitCodes.USAGE);
    }

    // Harness

    private static final class Captured {
        private final int code;
        private final byte[] out;
        private final String err;

        Captured(int code, byte[] out, String err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }

        int code() {
            return code;
        }

        byte[] out() {
            return out;
        }

        String err() {
            return err;
        }

        String outText() {
            return new String(out, StandardCharsets.UTF_8);
        }
    }

    private static Captured run(String... args) {
        return run(null, args);
    }

    private static Captured run(byte @Nullable [] stdin, String... args) {
        var outBuf = new ByteArrayOutputStream();
        var errBuf = new ByteArrayOutputStream();
        var oldOut = System.out;
        var oldErr = System.err;
        var oldIn = System.in;
        System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        if (stdin != null) {
            System.setIn(new ByteArrayInputStream(stdin));
        }
        try {
            int code = SpreadCommand.run(args);
            return new Captured(
                    code, outBuf.toByteArray(), errBuf.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            System.setIn(oldIn);
        }
    }
}
