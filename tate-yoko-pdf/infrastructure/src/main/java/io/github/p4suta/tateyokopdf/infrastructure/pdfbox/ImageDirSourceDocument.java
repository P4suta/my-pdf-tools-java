package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.PageDimension;
import io.github.p4suta.tateyokopdf.port.PageContent;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SourceDocument} backed by a directory of registered bitonal page images (TIFF-G4), read
 * in filename order. Each page's size is reported in points ({@code pixels / dpi * 72}) so the
 * spread layout — computed in points exactly as for a PDF source — places the pages correctly. The
 * dpi is injected (the resolved scan resolution), not trusted from the TIFF tag round-trip.
 *
 * <p>Pairs with {@link ImageDirDocumentFactory} (which constructs it) and {@link
 * PdfBoxSpreadDocument} (which embeds each {@link ImagePageContent} as CCITT G4). This lets {@code
 * SpreadService} build RTL spreads straight from the pipeline's registered output with no
 * intermediate PDF.
 */
final class ImageDirSourceDocument implements SourceDocument {

    private static final Logger log = LoggerFactory.getLogger(ImageDirSourceDocument.class);

    private final List<ImagePageContent> pages;
    private final DocumentMetadata metadata;

    ImageDirSourceDocument(Path dir, String glob, int dpi, DocumentMetadata metadata) {
        this.metadata = metadata;
        this.pages = loadPages(dir, glob, dpi);
        if (pages.isEmpty()) {
            throw SpreadException.withDetail(
                    ErrorKind.PDF_NOT_FOUND, "no images matching " + glob + " in " + dir, null);
        }
        log.info("Image source: {} page(s) from {}", pages.size(), dir.getFileName());
    }

    private static List<ImagePageContent> loadPages(Path dir, String glob, int dpi) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path p : stream) {
                files.add(p);
            }
        } catch (IOException e) {
            throw SpreadException.withDetail(ErrorKind.PDF_NOT_FOUND, "cannot list " + dir, e);
        }
        // Filename order == reading order: the extractor names pages page-cNN-..., zero-padded.
        files.sort(Comparator.comparing(Path::toString));
        List<ImagePageContent> result = new ArrayList<>(files.size());
        for (Path f : files) {
            int[] wh = pixelSize(f);
            float widthPt = wh[0] / (float) dpi * 72f;
            float heightPt = wh[1] / (float) dpi * 72f;
            result.add(new ImagePageContent(f, widthPt, heightPt));
        }
        return result;
    }

    /** {@return the pixel {width, height} of a bitonal image, read from its header via ImageIO} */
    private static int[] pixelSize(Path file) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) {
                throw new IOException("no ImageIO stream for " + file);
            }
            var readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("no ImageIO reader for " + file);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw SpreadException.withDetail(
                    ErrorKind.PDF_CORRUPTED, "unreadable image " + file, e);
        }
    }

    @Override
    public int pageCount() {
        return pages.size();
    }

    @Override
    public PageDimension pageDimension(int index) {
        ImagePageContent p = pages.get(index);
        return new PageDimension(p.widthPt(), p.heightPt());
    }

    @Override
    public PageContent pageContent(int index) {
        return pages.get(index);
    }

    @Override
    public DocumentMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() {
        // Nothing to release: pages are plain file references read on demand by the output adapter.
    }
}
