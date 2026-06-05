package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import io.github.p4suta.tateyokopdf.domain.model.DocumentMetadata;
import io.github.p4suta.tateyokopdf.domain.model.MemoryMode;
import io.github.p4suta.tateyokopdf.domain.model.PdfVersion;
import io.github.p4suta.tateyokopdf.port.DocumentFactory;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import io.github.p4suta.tateyokopdf.port.SpreadDocument;
import java.nio.file.Path;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction;

/**
 * A {@link DocumentFactory} whose source is a directory of registered bitonal page images rather
 * than a PDF — so {@code SpreadService} can compose RTL spreads straight from the pipeline's
 * registered output with no intermediate PDF. {@link #openSource(Path)} treats its argument as the
 * image directory; the {@code dpi} (the resolved scan resolution) and source {@link
 * DocumentMetadata} are supplied at construction.
 *
 * <p>Output reuses {@link PdfBoxSpreadDocument} (configured with the target {@link PdfVersion} and
 * a {@link MemoryMode}-selected stream cache); it embeds each page as CCITT G4 — see its {@code
 * addSpread}.
 */
public final class ImageDirDocumentFactory implements DocumentFactory {

    private final String glob;
    private final int dpi;
    private final DocumentMetadata metadata;
    private final PdfVersion version;
    private final MemoryMode memoryMode;

    /**
     * @param glob filename glob selecting the page images (register writes {@code *.tiff})
     * @param dpi the resolved scan resolution, used to convert pixels to points
     * @param metadata document metadata to carry onto the output (e.g. inherited from the source
     *     PDF)
     * @param version the output PDF version
     * @param memoryMode whether PDFBox caches output streams on the heap or in a temp file
     */
    public ImageDirDocumentFactory(
            String glob,
            int dpi,
            DocumentMetadata metadata,
            PdfVersion version,
            MemoryMode memoryMode) {
        this.glob = glob;
        this.dpi = dpi;
        this.metadata = metadata;
        this.version = version;
        this.memoryMode = memoryMode;
    }

    @Override
    public SourceDocument openSource(Path path) {
        return new ImageDirSourceDocument(path, glob, dpi, metadata);
    }

    @Override
    public SpreadDocument createOutput() {
        return new PdfBoxSpreadDocument(version, streamCache());
    }

    private StreamCacheCreateFunction streamCache() {
        return memoryMode == MemoryMode.SCRATCH_FILE
                ? IOUtils.createTempFileOnlyStreamCache()
                : IOUtils.createMemoryOnlyStreamCache();
    }
}
