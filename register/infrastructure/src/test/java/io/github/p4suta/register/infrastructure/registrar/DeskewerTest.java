package io.github.p4suta.register.infrastructure.registrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.infrastructure.TestImages;
import io.github.p4suta.register.infrastructure.registrar.Deskewer.SkewEstimate;
import io.github.p4suta.shared.imaging.Pix;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the register deskew policy layered on the shared {@link Pix} primitives: its observable
 * behavior and its confidence/angle gate.
 */
class DeskewerTest {

    @Test
    void deskewPreservesPageDimensions(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("col.pbm");
        TestImages.writePbm(file, TestImages.pageWithColumn(60, 80, 20, 10, 39, 69));
        try (Pix pix = Pix.read(file);
                Pix deskewed = Deskewer.deskew(pix)) {
            // The 90-degree round trip and the same-size straightening rotation both preserve the
            // page's dimensions, whether or not a tilt was corrected.
            assertEquals(60, deskewed.width());
            assertEquals(80, deskewed.height());
        }
    }

    @Test
    void measureSkewProducesAFiniteEstimate(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("col.pbm");
        TestImages.writePbm(file, TestImages.pageWithColumn(60, 80, 20, 10, 39, 69));
        try (Pix pix = Pix.read(file)) {
            SkewEstimate est = Deskewer.measureSkew(pix);
            assertTrue(Double.isFinite(est.angleDeg()), "angle is finite");
            assertTrue(Double.isFinite(est.conf()), "confidence is finite");
        }
    }

    @Test
    void correctableGatesOnConfidenceAndAngle() {
        // A reliable, real tilt is correctable.
        assertTrue(new SkewEstimate(2.0, 2.0, true).correctable());
        // No estimate was produced: never correctable, whatever the angle/confidence.
        assertFalse(new SkewEstimate(2.0, 9.9, false).correctable());
        // Confidence below the 1.5 floor: treated as noise.
        assertFalse(new SkewEstimate(2.0, 1.4, true).correctable());
        // Angle below the 0.3-degree floor: within scan noise, left alone.
        assertFalse(new SkewEstimate(0.2, 5.0, true).correctable());
        // Angle beyond the 8.0-degree ceiling: treated as a misdetection.
        assertFalse(new SkewEstimate(8.1, 5.0, true).correctable());
        // The boundaries themselves are inclusive.
        assertTrue(new SkewEstimate(0.3, 1.5, true).correctable());
        assertTrue(new SkewEstimate(-8.0, 1.5, true).correctable());
    }
}
