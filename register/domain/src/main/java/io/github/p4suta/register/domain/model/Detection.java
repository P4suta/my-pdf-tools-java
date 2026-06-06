package io.github.p4suta.register.domain.model;

/**
 * A main-column detection result: the column box and the row-projection band its vertical extent
 * came from. The band lets diagnostics re-derive the projection profiles (row ink over the page,
 * column ink within the band) without holding large arrays.
 *
 * <p>The detector that produces it (intersecting the row and column ink profiles) lives in {@code
 * :infrastructure}, because reading the ink profiles requires the Leptonica binding.
 */
public record Detection(Box column, Band verticalBand) {}
