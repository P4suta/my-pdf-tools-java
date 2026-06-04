package io.github.p4suta.tateyokopdf.domain.model;

/**
 * Which half of the spread a lone (unpaired) page occupies; the opposite half is left blank.
 *
 * <p>{@link #LEADING} is the reading direction's starting side (RTL → right, LTR → left) — the
 * natural place for a standalone cover or an odd trailing page. {@link #TRAILING} is the opposite
 * side, used for a "leading blank" opening where page 1 sits across from an implied blank.
 */
public enum SpreadHalf {
    LEADING,
    TRAILING
}
