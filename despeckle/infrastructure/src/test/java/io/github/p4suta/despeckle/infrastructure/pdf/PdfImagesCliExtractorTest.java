package io.github.p4suta.despeckle.infrastructure.pdf;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.p4suta.despeckle.port.PdfImageExtractor;
import org.junit.jupiter.api.Test;

/**
 * Adapter-level coverage for {@link PdfImagesCliExtractor}.
 *
 * <p>The parser logic lives in the shared {@code io.github.p4suta.shared.pdf.PdfListingParser},
 * exercised by the {@code :shared:pdf} test suite. {@link PdfImageExtractor#dominantDpi} and {@link
 * PdfImageExtractor#extract} only drive the external {@code pdfinfo}/{@code pdfimages} binaries
 * over a real PDF, with no pure branch left to assert against, and there is no in-module PDF
 * fixture (the testFixtures carry only {@code TestImages}); the end-to-end exercise of this adapter
 * lives in {@code :app}'s pipeline tests. All that can be pinned here without a real PDF and the
 * native toolchain is the adapter's construction and port contract.
 */
final class PdfImagesCliExtractorTest {

    @Test
    void constructsAsAPdfImageExtractor() {
        assertInstanceOf(PdfImageExtractor.class, new PdfImagesCliExtractor());
    }
}
