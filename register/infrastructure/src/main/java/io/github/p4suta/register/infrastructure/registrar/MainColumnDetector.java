package io.github.p4suta.register.infrastructure.registrar;

import io.github.p4suta.register.domain.model.Band;
import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.Detection;
import io.github.p4suta.register.domain.service.ProjectionProfile;
import io.github.p4suta.shared.imaging.Pix;
import java.util.Optional;

/**
 * Detects a page's main text column by intersecting a vertical band (from the row ink profile) with
 * a horizontal band (from the column ink profile taken within that vertical band). Restricting the
 * column profile to the body's vertical band is what keeps sparse marginalia — running titles and
 * footnote indices — out of the detected box.
 *
 * <p>Lives in {@code :infrastructure}: reading the ink profiles requires the Leptonica {@link Pix}
 * binding. The reduction it drives ({@link ProjectionProfile}) and the {@link Detection} it returns
 * are pure domain types.
 */
public final class MainColumnDetector {

    public MainColumnDetector() {}

    /**
     * The detected main column with its vertical band, or empty if the page carries no detectable
     * body text.
     *
     * @param page the (already deskewed) page to analyze
     * @param dpi the page's scan resolution; the running-title gutter is about {@code dpi/8} px,
     *     the gap width that blocks a band, so it must be sized in the page's own pixels
     */
    public Optional<Detection> detect(Pix page, int dpi) {
        int blockingGap = Math.max(1, Math.round(dpi / 8.0f));
        int[] rowInk = page.inkByRow();
        int height = rowInk.length;
        int width = page.width();

        Optional<Band> vertical =
                ProjectionProfile.densestBand(rowInk, Math.max(1, height / 32), blockingGap);
        if (vertical.isEmpty()) {
            return Optional.empty();
        }
        int y0 = vertical.get().start();
        int y1 = vertical.get().endExclusive();

        try (Pix band = page.clip(0, y0, width, y1 - y0)) {
            int[] columnInk = band.inkByColumn();
            // Horizontal extent: the full inked width (bbox), so a chapter-interior page whose body
            // the gutter heuristic would fragment still yields the whole column, not a sub-run.
            Optional<Band> horizontal = ProjectionProfile.inkBounds(columnInk);
            if (horizontal.isEmpty()) {
                return Optional.empty();
            }
            int x0 = horizontal.get().start();
            int x1 = horizontal.get().endExclusive();
            return Optional.of(new Detection(new Box(x0, y0, x1 - x0, y1 - y0), vertical.get()));
        }
    }
}
