package io.github.p4suta.tateyokopdf.application;

/**
 * The {@code Producer} string this app writes into the PDF Info dictionary of every output. Single
 * source of truth so the value is not inlined at multiple call sites.
 */
public final class Producer {

    public static final String NAME = "tate-yoko-pdf";

    private Producer() {}
}
