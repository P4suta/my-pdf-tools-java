package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.p4suta.tateyokopdf.port.SourceDocument;
import io.github.p4suta.tateyokopdf.testfixtures.PdfFixtures;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PdfBoxSourceDocumentTest {

    private final PdfBoxDocumentFactory factory = new PdfBoxDocumentFactory();

    @Test
    void pageCountMatchesFixture(@TempDir Path tmp) throws Exception {
        try (SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "n.pdf", 5))) {
            assertThat(src.pageCount()).isEqualTo(5);
        }
    }

    @Test
    void pageDimensionForA4(@TempDir Path tmp) throws Exception {
        try (SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "a4.pdf", 1))) {
            var dim = src.pageDimension(0);
            // A4: 595 x 842 pt (approx)
            assertThat(dim.widthPt()).isEqualTo(595f, org.assertj.core.api.Assertions.within(0.5f));
            assertThat(dim.heightPt())
                    .isEqualTo(842f, org.assertj.core.api.Assertions.within(0.5f));
        }
    }

    @Test
    void pageDimensionSwapsForRotation90(@TempDir Path tmp) throws Exception {
        try (SourceDocument src = factory.openSource(PdfFixtures.rotated(tmp, "r90.pdf", 90))) {
            var dim = src.pageDimension(0);
            // After swap, width should equal A4 height
            assertThat(dim.widthPt()).isEqualTo(842f, org.assertj.core.api.Assertions.within(0.5f));
            assertThat(dim.heightPt())
                    .isEqualTo(595f, org.assertj.core.api.Assertions.within(0.5f));
        }
    }

    @Test
    void pageDimensionSwapsForRotation270(@TempDir Path tmp) throws Exception {
        try (SourceDocument src = factory.openSource(PdfFixtures.rotated(tmp, "r270.pdf", 270))) {
            var dim = src.pageDimension(0);
            assertThat(dim.widthPt()).isEqualTo(842f, org.assertj.core.api.Assertions.within(0.5f));
        }
    }

    @Test
    void pageContentReturnsPdfBoxPageContent(@TempDir Path tmp) throws Exception {
        try (SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "p.pdf", 1))) {
            assertThat(src.pageContent(0)).isInstanceOf(PdfBoxPageContent.class);
        }
    }

    @Test
    void closeIsIdempotent(@TempDir Path tmp) throws Exception {
        SourceDocument src = factory.openSource(PdfFixtures.multiPageA4(tmp, "p.pdf", 1));
        src.close();
        assertThatNoException().isThrownBy(src::close);
    }

    @Test
    void metadataRoundTripsAllPreservedFields(@TempDir Path tmp) throws Exception {
        Instant created = Instant.parse("2024-04-01T12:00:00Z");
        Path src =
                PdfFixtures.withMetadata(
                        tmp,
                        "in.pdf",
                        info -> {
                            // Japanese strings exercise PDFBox's auto-switch from PDFDocEncoding to
                            // UTF-16BE —
                            // an ASCII-only fixture would not catch a future regression in that
                            // path.
                            info.setTitle("見開き化テスト");
                            info.setAuthor("テスト著者");
                            info.setSubject("subject");
                            info.setKeywords("a, b, c");
                            info.setCreator("Microsoft Word");
                            info.setCreationDate(
                                    GregorianCalendar.from(created.atZone(ZoneOffset.UTC)));
                        });
        try (SourceDocument doc = factory.openSource(src)) {
            var meta = doc.metadata();
            assertThat(meta.title()).contains("見開き化テスト");
            assertThat(meta.author()).contains("テスト著者");
            assertThat(meta.subject()).contains("subject");
            assertThat(meta.keywords()).contains("a, b, c");
            assertThat(meta.creator()).contains("Microsoft Word");
            assertThat(meta.creationDate()).contains(created);
        }
    }

    @Test
    void metadataIsAllEmptyForFixtureWithoutInfoDict(@TempDir Path tmp) throws Exception {
        try (SourceDocument doc =
                factory.openSource(PdfFixtures.multiPageA4(tmp, "plain.pdf", 1))) {
            var meta = doc.metadata();
            assertThat(meta.title()).isEmpty();
            assertThat(meta.author()).isEmpty();
            assertThat(meta.creator()).isEmpty();
            assertThat(meta.language()).isEmpty();
            // Note: PDFBox auto-fills CreationDate on save() even without explicit setter, so the
            // fixture-created PDF carries a CreationDate. metadata().creationDate() therefore is
            // present here — not part of this assertion.
        }
    }
}
