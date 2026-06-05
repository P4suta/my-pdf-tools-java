package io.github.p4suta.register.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The on-disk extension each format maps to. This switch is the contract {@code PdfOutputNaming}
 * and {@code CorpusFiles.mirrorDestination} rely on to name registered pages, so its four arms are
 * pinned here rather than only exercised transitively.
 */
class OutputFormatTest {

    @Test
    void sameKeepsTheInputExtensionBySignalingNull() {
        assertNull(OutputFormat.SAME.extension());
    }

    @Test
    void explicitFormatsMapToTheirFileExtension() {
        assertEquals("pbm", OutputFormat.PBM.extension());
        assertEquals("png", OutputFormat.PNG.extension());
        assertEquals("tiff", OutputFormat.TIFF.extension());
        assertEquals("webp", OutputFormat.WEBP.extension());
    }
}
