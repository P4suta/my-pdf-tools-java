package io.github.p4suta.webapp.domain;

/** Which side page one of the book opens on. */
public enum FirstPage {
    /** Page one opens on the right (the standard opening). */
    RIGHT,
    /** Page one opens on the left, preceded by a leading blank. */
    LEFT,
    /** Page one stands alone as a cover. */
    COVER
}
