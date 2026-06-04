package io.github.p4suta.tateyokopdf.application;

/**
 * The {@code Producer} string this app writes into the PDF Info dictionary of every output. Single
 * source of truth so the value is not inlined at multiple call sites. Embedding a build version
 * (e.g. {@code "tate-yoko-pdf 1.0 (PDFBox 3.0.7)"}) is deliberately deferred: it requires manifest
 * wiring that is unrelated to metadata inheritance and can be added in a follow-up.
 */
public final class Producer {

    public static final String NAME = "tate-yoko-pdf";

    private Producer() {}
}
