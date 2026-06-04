package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.PdfVersion;
import io.github.p4suta.tateyokopdf.domain.model.SpreadSpec;
import io.github.p4suta.tateyokopdf.port.PagePlacement;
import io.github.p4suta.tateyokopdf.port.SpreadDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.List;
import javax.xml.transform.TransformerException;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.xmpbox.type.BadFieldValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDFBox-backed {@link SpreadDocument}: builds the output {@code PDDocument} one spread page at a
 * time, drawing imported source pages as form XObjects, applying metadata, optionally stamping
 * PDF/A structure (via {@link PdfAWriter}), and saving.
 */
public class PdfBoxSpreadDocument implements SpreadDocument {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxSpreadDocument.class);
    private final PDDocument document;
    private final PdfVersion version;

    PdfBoxSpreadDocument(PdfVersion version) {
        this(version, IOUtils.createMemoryOnlyStreamCache());
    }

    PdfBoxSpreadDocument(PdfVersion version, StreamCacheCreateFunction streamCache) {
        // streamCache decides where cloned page streams live: heap (memory-only) or a
        // temporary file (scratch). See MemoryMode / PdfBoxDocumentFactory.
        this.document = new PDDocument(streamCache);
        this.version = version;
        // PDFBox 3.0.7 quirk: for any value >= 1.4 this only updates the catalog /Version entry,
        // NOT the %PDF-x.x header byte. The header byte is rewritten downstream by qpdf
        // (--min-version=X.Y); together they yield a fully version-consistent output.
        this.document.setVersion(version.headerValue());
    }

    @Override
    public void addSpread(SpreadSpec spec, List<PagePlacement> placements) {
        var rect = new PDRectangle(spec.widthPt(), spec.heightPt());
        var page = new PDPage(rect);
        document.addPage(page);

        try (var cs = new PDPageContentStream(document, page)) {
            for (var placement : placements) {
                var pdfBoxContent = (PdfBoxPageContent) placement.content();
                PDFormXObject form = pdfBoxContent.importInto(document);

                cs.saveGraphicsState();
                cs.transform(
                        Matrix.getTranslateInstance(
                                placement.position().offsetXPt(),
                                placement.position().offsetYPt()));
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
        } catch (IOException e) {
            throw SpreadException.of(ErrorKind.PDF_WRITE_FAILED, e);
        }

        log.debug(
                "Added spread: {}x{} pt with {} placements",
                spec.widthPt(),
                spec.heightPt(),
                placements.size());
    }

    @Override
    public void applyMetadata(DocumentMetadata source, Instant modDate, String producer) {
        PDDocumentInformation info = document.getDocumentInformation();
        source.title().ifPresent(info::setTitle);
        source.author().ifPresent(info::setAuthor);
        source.subject().ifPresent(info::setSubject);
        source.keywords().ifPresent(info::setKeywords);
        source.creator().ifPresent(info::setCreator);
        source.creationDate()
                .ifPresent(
                        t ->
                                info.setCreationDate(
                                        GregorianCalendar.from(t.atZone(ZoneOffset.UTC))));
        info.setModificationDate(GregorianCalendar.from(modDate.atZone(ZoneOffset.UTC)));
        info.setProducer(producer);
        source.language().ifPresent(document.getDocumentCatalog()::setLanguage);
    }

    @Override
    public void finalizePdfA() {
        // PDF/A-2 is built on ISO 32000-1 (PDF 1.7). Refuse to stamp the pdfaid marker onto any
        // other
        // version rather than emit a file that claims conformance it cannot meet (e.g. if TARGET is
        // ever flipped to 2.0, which is the basis for PDF/A-4, not PDF/A-2).
        if (version != PdfVersion.PDF_1_7) {
            throw SpreadException.withDetail(
                    ErrorKind.INTERNAL,
                    "PDF/A-2b requires PDF 1.7 output, but version is " + version,
                    null);
        }
        try {
            new PdfAWriter().stamp(document);
        } catch (IOException | TransformerException | BadFieldValueException e) {
            throw SpreadException.of(ErrorKind.PDF_WRITE_FAILED, e);
        }
        log.debug("Marked output as PDF/A-2b (sRGB output intent + pdfaid XMP)");
    }

    @Override
    public void save(Path destination) {
        try {
            document.save(destination.toFile());
            log.info("Saved output to {}", destination.getFileName());
        } catch (IOException e) {
            throw SpreadException.withDetail(
                    ErrorKind.PDF_WRITE_FAILED, "destination=" + destination, e);
        }
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (Exception e) {
            log.warn("Failed to close output document", e);
        }
    }
}
