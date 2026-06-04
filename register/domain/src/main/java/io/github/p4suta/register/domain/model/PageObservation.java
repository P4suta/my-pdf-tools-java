package io.github.p4suta.register.domain.model;

/**
 * What the analysis pass learned about one page: its position in the corpus, its parity, and the
 * detected main-text-column bounding box. These feed the per-parity {@link
 * io.github.p4suta.register.domain.service.Reference}.
 *
 * @param index 0-based page index in reading order
 * @param parity recto or verso
 * @param mainColumn the detected main-text-column bounding box
 */
public record PageObservation(int index, Parity parity, Box mainColumn) {}
