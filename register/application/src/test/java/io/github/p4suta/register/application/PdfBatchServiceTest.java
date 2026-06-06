package io.github.p4suta.register.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.application.Fakes.FakeJbig2Assembler;
import io.github.p4suta.register.application.Fakes.FakePageRegistrar;
import io.github.p4suta.register.application.Fakes.FakePdfImageExtractor;
import io.github.p4suta.register.application.Fakes.RecordingReporterFactory;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.RegisterOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the batch file selection — no native tools needed. The selection must be
 * deterministic (directory iteration order is unspecified, so it is always sorted), flat (top-level
 * only), and case-insensitive on the extension.
 */
class PdfBatchTest {

    @Test
    void listsOnlyTopLevelPdfsInDeterministicNameOrder(@TempDir Path dir) throws Exception {
        // Created out of order; .pdf and .PDF both count, other files do not.
        Files.createFile(dir.resolve("b.pdf"));
        Files.createFile(dir.resolve("a.pdf"));
        Files.createFile(dir.resolve("C.PDF"));
        Files.createFile(dir.resolve("notes.txt"));
        Files.createFile(dir.resolve("scan.pdf.bak"));
        Path sub = Files.createDirectories(dir.resolve("sub"));
        Files.createFile(sub.resolve("deep.pdf")); // a nested PDF is ignored (flat selection)

        List<String> names =
                PdfBatchService.listPdfs(dir).stream()
                        .map(p -> Objects.requireNonNull(p.getFileName()).toString())
                        .toList();

        // Sorted by path string: ASCII order, so uppercase 'C' precedes lowercase 'a'/'b'.
        assertEquals(List.of("C.PDF", "a.pdf", "b.pdf"), names);
    }

    @Test
    void emptyDirectoryYieldsEmptyList(@TempDir Path dir) throws Exception {
        assertTrue(PdfBatchService.listPdfs(dir).isEmpty());
    }

    @Test
    void outputNameInsertsSuffixBeforeTheExtension() {
        assertEquals(
                "book_registered.pdf",
                PdfBatchService.outputName(Path.of("book.pdf"), "_registered"));
        // A non-empty suffix normalizes the extension case to .pdf.
        assertEquals(
                "book_registered.pdf",
                PdfBatchService.outputName(Path.of("book.PDF"), "_registered"));
        // Only the file name matters, and non-ASCII stems are preserved verbatim.
        assertEquals(
                "見本_registered.pdf",
                PdfBatchService.outputName(Path.of("scans/見本.pdf"), "_registered"));
    }

    @Test
    void outputNameKeepsTheOriginalNameWhenSuffixEmpty() {
        assertEquals("book.pdf", PdfBatchService.outputName(Path.of("book.pdf"), ""));
        assertEquals(
                "book.PDF", PdfBatchService.outputName(Path.of("book.PDF"), "")); // case preserved
    }

    @Test
    void isBatchInputDistinguishesADirectoryFromAFile(@TempDir Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("one.pdf"));
        assertTrue(PdfBatchService.isBatchInput(dir));
        assertFalse(PdfBatchService.isBatchInput(file));
    }

    // run(): the batch loop (continue-on-error, skip-on-exists, force) over fake ports

    private static PdfBatchService batchService(@Nullable String failOn) {
        FakePdfImageExtractor extractor = new FakePdfImageExtractor(2, 600, failOn);
        RegistrationService registration =
                new RegistrationService(
                        new FakePageRegistrar(true, 600, 2480, 3508),
                        new RecordingReporterFactory());
        PdfPipelineService pipeline =
                new PdfPipelineService(extractor, registration, new FakeJbig2Assembler());
        return new PdfBatchService(pipeline);
    }

    private static PdfBatchService.Config batchConfig(Path in, Path out, boolean force) {
        RegisterOptions options =
                new RegisterOptions(OptionalInt.of(600), null, true, true, 0.5, Anchor.TOP_RIGHT);
        return new PdfBatchService.Config(in, out, options, 2, force, "_registered");
    }

    private static void writePdfs(Path dir, String... names) throws Exception {
        for (String n : names) {
            Files.writeString(dir.resolve(n), "%PDF", StandardCharsets.UTF_8);
        }
    }

    @Test
    void anEmptyInputDirectoryRegistersNothing(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        PdfBatchService.Summary summary = batchService(null).run(batchConfig(in, out, false));
        assertEquals(new PdfBatchService.Summary(0, 0, 0), summary);
    }

    @Test
    void registersEveryBookAndWritesEachOutput(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePdfs(in, "a.pdf", "b.pdf");

        PdfBatchService.Summary summary = batchService(null).run(batchConfig(in, out, false));

        assertEquals(new PdfBatchService.Summary(2, 0, 0), summary);
        assertTrue(Files.exists(out.resolve("a_registered.pdf")));
        assertTrue(Files.exists(out.resolve("b_registered.pdf")));
    }

    @Test
    void skipsBooksWhoseOutputAlreadyExistsUnlessForced(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = Files.createDirectories(tmp.resolve("out"));
        writePdfs(in, "a.pdf", "b.pdf");
        // a's output already exists; without force it is skipped, b is registered.
        Files.writeString(out.resolve("a_registered.pdf"), "old", StandardCharsets.UTF_8);

        PdfBatchService.Summary summary = batchService(null).run(batchConfig(in, out, false));

        assertEquals(new PdfBatchService.Summary(1, 1, 0), summary);
        // The pre-existing output was left untouched (skipped, not regenerated).
        assertEquals("old", Files.readString(out.resolve("a_registered.pdf")));
    }

    @Test
    void forceRegeneratesAnExistingOutput(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = Files.createDirectories(tmp.resolve("out"));
        writePdfs(in, "a.pdf");
        Files.writeString(out.resolve("a_registered.pdf"), "old", StandardCharsets.UTF_8);

        PdfBatchService.Summary summary = batchService(null).run(batchConfig(in, out, true));

        assertEquals(new PdfBatchService.Summary(1, 0, 0), summary);
        assertTrue(Files.readString(out.resolve("a_registered.pdf")).startsWith("%PDF-fake"));
    }

    @Test
    void continuesPastABookThatFailsAndCountsIt(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        writePdfs(in, "good.pdf", "bad.pdf");
        // The extractor fails on "bad": that book is counted failed, the other still registers.
        PdfBatchService.Summary summary = batchService("bad").run(batchConfig(in, out, false));

        assertEquals(new PdfBatchService.Summary(1, 0, 1), summary);
        assertTrue(Files.exists(out.resolve("good_registered.pdf")));
    }
}
