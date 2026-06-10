package io.github.p4suta.despeckle.domain.model;

import io.github.p4suta.shared.kernel.Resolution;
import io.github.p4suta.shared.kernel.Validators;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Per-page despeckle knobs.
 *
 * <p>The single meaningful parameter is the speck size: connected components whose bounding box is
 * at most this many pixels in both width and height are treated as scanner dust. It is derived from
 * the scan resolution unless overridden explicitly via {@code speckSizePx}.
 *
 * <p>The resolution itself is resolved per page, in precedence order: an explicit {@code dpi} wins;
 * otherwise the page's own embedded resolution (TIFF/PNG tag) is used; and when neither is known,
 * the filter falls back to {@link #DEFAULT_DPI} but no resolution is asserted on the output.
 *
 * <p>A second, opt-in pass removes <em>isolated</em> medium specks — components small enough to be
 * dust that sit on clean background, away from any real text. Because punctuation, dakuten and ruby
 * always hug a glyph, they are never isolated and so are never removed by it; this lets the speck
 * size for clean margins be far more aggressive than a global filter safely could.
 *
 * @param dpi an explicit scan resolution, or empty to read each page's embedded resolution
 * @param speckSizePx an explicit speck size in pixels, or empty to derive it from the resolution
 * @param fillHoles whether to fill isolated white pin-holes inside black strokes
 * @param removeIsolatedDust whether to run the isolated-medium-dust pass
 * @param isolatedDustSizePx an explicit max size for an isolated speck, or empty to derive it; a
 *     present value also implies {@code removeIsolatedDust}
 * @param collectComponentStats whether to count 8-connected components before and after — two of
 *     the most expensive full-page scans, so off when nothing consumes the counts (no report)
 */
public record ProcessOptions(
        OptionalInt dpi,
        OptionalInt speckSizePx,
        boolean fillHoles,
        boolean removeIsolatedDust,
        OptionalInt isolatedDustSizePx,
        boolean collectComponentStats) {

    /** Resolution assumed for the speck filter when neither a flag nor the image supplies one. */
    public static final int DEFAULT_DPI = 300;

    public ProcessOptions {
        if (dpi.isPresent()) {
            Validators.requirePositive(dpi.getAsInt(), "dpi");
        }
        if (speckSizePx.isPresent()) {
            Validators.requirePositive(speckSizePx.getAsInt(), "speckSizePx");
        }
        if (isolatedDustSizePx.isPresent()) {
            Validators.requirePositive(isolatedDustSizePx.getAsInt(), "isolatedDustSizePx");
        }
    }

    /** The five-knob shape every direct caller uses; component counting defaults to on. */
    public ProcessOptions(
            OptionalInt dpi,
            OptionalInt speckSizePx,
            boolean fillHoles,
            boolean removeIsolatedDust,
            OptionalInt isolatedDustSizePx) {
        this(dpi, speckSizePx, fillHoles, removeIsolatedDust, isolatedDustSizePx, true);
    }

    /** Options with the isolated-dust pass off — the common case. */
    public static ProcessOptions of(OptionalInt dpi, OptionalInt speckSizePx, boolean fillHoles) {
        return new ProcessOptions(dpi, speckSizePx, fillHoles, false, OptionalInt.empty());
    }

    /**
     * A copy with {@code dpi} set. The pipeline uses this to pin the scan's detected DPI onto every
     * page, so the speck filter sizes correctly off images that {@code pdfimages} left tagged at a
     * default 72 dpi.
     */
    public ProcessOptions withDpi(int dpi) {
        return new ProcessOptions(
                OptionalInt.of(dpi),
                speckSizePx,
                fillHoles,
                removeIsolatedDust,
                isolatedDustSizePx,
                collectComponentStats);
    }

    /**
     * A copy with component counting disabled — for runs where nothing consumes the counts (no
     * report), saving two full connected-component labelings per page.
     */
    public ProcessOptions withoutComponentStats() {
        return new ProcessOptions(
                dpi, speckSizePx, fillHoles, removeIsolatedDust, isolatedDustSizePx, false);
    }

    /**
     * Default options: auto-detect resolution, derived speck size, hole-filling on, and the
     * isolated-dust pass on (it only clears genuinely isolated specks, never typography).
     */
    public static ProcessOptions defaults() {
        return new ProcessOptions(
                OptionalInt.empty(), OptionalInt.empty(), true, true, OptionalInt.empty());
    }

    /**
     * The resolution to honor for a page whose embedded resolution is {@code img} (empty if none).
     * An explicit {@code --dpi} wins; otherwise the image's own resolution; otherwise empty. This
     * is the value stamped onto the output, so a guessed fallback is <em>not</em> reported here —
     * only a known resolution.
     */
    public Optional<Resolution> resolution(Optional<Resolution> img) {
        if (dpi.isPresent()) {
            return Optional.of(Resolution.of(dpi.getAsInt()));
        }
        return img;
    }

    /**
     * The speck-size threshold in pixels for a page whose embedded resolution is {@code img}. An
     * explicit speck size wins; otherwise it scales with the resolved resolution (~3 px at 300 dpi,
     * ~6 px at 600 dpi), assuming {@link #DEFAULT_DPI} when nothing is known.
     */
    public int speckSize(Optional<Resolution> img) {
        if (speckSizePx.isPresent()) {
            return speckSizePx.getAsInt();
        }
        int effectiveDpi = resolution(img).map(Resolution::dpi).orElse(DEFAULT_DPI);
        return Math.max(1, Math.round(effectiveDpi / 100.0f));
    }

    /** Whether the isolated-medium-dust pass runs for this configuration. */
    public boolean isolatedDustEnabled() {
        return removeIsolatedDust || isolatedDustSizePx.isPresent();
    }

    /**
     * The largest bounding box (in either axis) an isolated speck may have to still count as dust.
     * An explicit value wins; otherwise it scales with resolution (~15 px at 600 dpi), kept below
     * the smallest real glyph so only genuine specks qualify.
     */
    public int isolatedDustSize(Optional<Resolution> img) {
        if (isolatedDustSizePx.isPresent()) {
            return isolatedDustSizePx.getAsInt();
        }
        int effectiveDpi = resolution(img).map(Resolution::dpi).orElse(DEFAULT_DPI);
        return Math.max(speckSize(img) + 1, Math.round(effectiveDpi / 40.0f));
    }

    /**
     * How close (in pixels) a speck must be to real text to be spared. A speck within this distance
     * of a kept component is assumed to belong to it (dakuten, punctuation, ruby) and is preserved;
     * only specks farther out on clean background are removed. Sized to cover the speck plus the
     * typical gap to its glyph.
     */
    public int isolatedDustProximity(Optional<Resolution> img) {
        return isolatedDustSize(img) + speckSize(img);
    }
}
