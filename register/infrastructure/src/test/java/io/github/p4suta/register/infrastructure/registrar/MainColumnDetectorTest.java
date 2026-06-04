package io.github.p4suta.register.infrastructure.registrar;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.infrastructure.TestImages;
import io.github.p4suta.shared.imaging.Pix;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FFM-backed test that the running title (柱) above the body is excluded from the main column. */
class MainColumnDetectorTest {

    @Test
    void excludesRunningTitleAboveTheBody(@TempDir Path tmp) throws Exception {
        boolean[][] page = TestImages.blank(200, 300);
        // A short running-title strip near the top edge...
        TestImages.fillRect(page, 90, 10, 110, 30);
        // ...separated by a wide blank gutter from the much denser body column below.
        TestImages.fillRect(page, 80, 71, 130, 280);
        Path file = tmp.resolve("page.pbm");
        TestImages.writePbm(file, page);

        try (Pix pix = Pix.read(file)) {
            Box box = new MainColumnDetector().detect(pix, 300).orElseThrow().column();
            // The detected band is the body, beginning below the running title (which ends at
            // y=30).
            assertTrue(
                    box.y() > 40,
                    "main column should start below the running title, got y=" + box.y());
            assertTrue(
                    box.bottom() > 250,
                    "main column should span the body, got bottom=" + box.bottom());
            // Horizontally it is the wide body column (~80..130), not the narrow strip.
            assertTrue(box.w() > 30, "column width should be the body's, got " + box.w());
        }
    }
}
