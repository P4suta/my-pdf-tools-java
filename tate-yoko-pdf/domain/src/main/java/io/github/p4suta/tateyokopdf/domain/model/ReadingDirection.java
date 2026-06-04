package io.github.p4suta.tateyokopdf.domain.model;

public enum ReadingDirection {
    /** Right-to-left (Japanese vertical text). First page placed on right side. */
    RTL,
    /** Left-to-right (horizontal text). First page placed on left side. */
    LTR;

    public static final ReadingDirection DEFAULT = RTL;
}
