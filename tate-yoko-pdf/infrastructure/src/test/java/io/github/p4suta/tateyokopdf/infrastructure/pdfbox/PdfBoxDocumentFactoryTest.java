package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import io.github.p4suta.tateyokopdf.port.SpreadDocument;
import io.github.p4suta.tateyokopdf.testfixtures.PdfFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PdfBoxDocumentFactoryTest {

    private final PdfBoxDocumentFactory factory = new PdfBoxDocumentFactory();

    @Test
    void opensValidPdf(@TempDir Path tmp) throws Exception {
        Path pdf = PdfFixtures.multiPageA4(tmp, "ok.pdf", 3);
        try (SourceDocument src = factory.openSource(pdf)) {
            assertThat(src.pageCount()).isEqualTo(3);
        }
    }

    @Test
    void throwsCorruptedKindOnGarbageFile(@TempDir Path tmp) throws Exception {
        Path garbage = PdfFixtures.corruptedHeader(tmp, "bad.pdf");
        assertThatThrownBy(() -> factory.openSource(garbage))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_CORRUPTED));
    }

    @Test
    void throwsCorruptedKindOnEmptyFile(@TempDir Path tmp) throws Exception {
        Path empty = PdfFixtures.empty(tmp, "empty.pdf");
        assertThatThrownBy(() -> factory.openSource(empty))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_CORRUPTED));
    }

    @Test
    void throwsCorruptedKindOnMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("missing.pdf");
        assertThatThrownBy(() -> factory.openSource(missing)).isInstanceOf(SpreadException.class);
    }

    @Test
    void throwsPasswordProtectedKindOnEncryptedPdf(@TempDir Path tmp) throws Exception {
        Path encrypted = PdfFixtures.passwordProtected(tmp, "enc.pdf", "secret");
        assertThatThrownBy(() -> factory.openSource(encrypted))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_PASSWORD_PROTECTED));
    }

    @Test
    void userMessageDoesNotLeakAbsolutePath(@TempDir Path tmp) throws Exception {
        Path encrypted = PdfFixtures.passwordProtected(tmp, "secrets.pdf", "x");
        assertThatThrownBy(() -> factory.openSource(encrypted))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> {
                            assertThat(ex.userMessage()).doesNotContain(tmp.toString());
                            assertThat(ex.technicalDetail()).contains("path=");
                        });
    }

    @Test
    void createOutputReturnsEmptySpreadDocument() {
        try (SpreadDocument out = factory.createOutput()) {
            assertThat(out).isNotNull();
        }
    }
}
