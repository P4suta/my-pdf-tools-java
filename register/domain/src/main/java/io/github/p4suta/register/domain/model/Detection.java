package io.github.p4suta.register.domain.model;

/**
 * A main-column detection result: the detected main-column box and the row-projection band its
 * vertical extent came from. The band lets diagnostics re-derive the projection profiles (row ink
 * over the whole page, column ink within the band) for visualization without holding large arrays.
 *
 * <p>A pure value type. The detector that produces it (intersecting the row and column ink
 * profiles) lives in {@code :infrastructure}, because reading the ink profiles requires the
 * Leptonica binding.
 *
 * @param column the main-column bounding box
 * @param verticalBand the row-projection band the column's vertical extent came from
 */
public record Detection(Box column, Band verticalBand) {}
