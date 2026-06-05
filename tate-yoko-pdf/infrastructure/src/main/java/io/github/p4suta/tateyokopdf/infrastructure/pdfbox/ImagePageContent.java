package io.github.p4suta.tateyokopdf.infrastructure.pdfbox;

import io.github.p4suta.tateyokopdf.port.PageContent;
import java.nio.file.Path;

/**
 * Image-backed {@link PageContent}: one registered bitonal page on disk plus its displayed size in
 * points. The matching output adapter ({@link PdfBoxSpreadDocument}) downcasts to this and embeds
 * the file as a CCITT G4 image XObject, so register's lossless G4 bytes are carried through without
 * re-encoding.
 *
 * @param path the page image file (a TIFF-G4 written by register)
 * @param widthPt the page width in points (pixels / dpi * 72)
 * @param heightPt the page height in points (pixels / dpi * 72)
 */
record ImagePageContent(Path path, float widthPt, float heightPt) implements PageContent {}
