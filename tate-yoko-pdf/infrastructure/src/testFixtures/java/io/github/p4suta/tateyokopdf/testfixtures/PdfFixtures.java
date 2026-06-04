package io.github.p4suta.tateyokopdf.testfixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class PdfFixtures {

    private PdfFixtures() {}

    /** A4 縦の N ページ PDF。各ページに "Page i / N" を描画 */
    public static Path multiPageA4(Path dir, String name, int pages) throws IOException {
        Path path = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                writeNumberedPage(doc, PDRectangle.A4, i, pages);
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * N ページの空白 A4 PDF (コンテンツストリーム・フォントなし)。フォント未埋め込みの Standard 14 を使う {@link #multiPageA4}
     * と違い、フォント・デバイス色を持たないので PDF/A 適合のグリーンパス検証に使える。
     */
    public static Path blankPages(Path dir, String name, int pages) throws IOException {
        Path path = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /** 異なるページサイズが混在した PDF (A4, A5, A3 をローテーション) */
    public static Path mixedSizes(Path dir, String name, int pages) throws IOException {
        Path path = dir.resolve(name);
        PDRectangle[] sizes = {PDRectangle.A4, PDRectangle.A5, PDRectangle.A3};
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                writeNumberedPage(doc, sizes[(i - 1) % sizes.length], i, pages);
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /** rotation 角度を指定して 1 ページ書く */
    public static Path rotated(Path dir, String name, int rotation) throws IOException {
        Path path = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            page.setRotation(rotation);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 36);
                cs.newLineAtOffset(80, 400);
                cs.showText("rotated " + rotation);
                cs.endText();
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /** パスワード保護 PDF を生成 (open password) */
    public static Path passwordProtected(Path dir, String name, String userPassword)
            throws IOException {
        Path path = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            writeNumberedPage(doc, PDRectangle.A4, 1, 1);
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy spp =
                    new StandardProtectionPolicy(userPassword, userPassword, ap);
            spp.setEncryptionKeyLength(128);
            spp.setPermissions(ap);
            doc.protect(spp);
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * 1 ページ A4 の PDF を生成し、呼出側が {@link PDDocumentInformation} に好きな field を載せられる builder。
     * メタデータ継承のテストで必要な field だけ pinpoint して fixture 化できる。
     */
    public static Path withMetadata(Path dir, String name, Consumer<PDDocumentInformation> mutator)
            throws IOException {
        Path path = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            writeNumberedPage(doc, PDRectangle.A4, 1, 1);
            mutator.accept(doc.getDocumentInformation());
            doc.save(path.toFile());
        }
        return path;
    }

    /** 完全に空の (0 byte) ファイル */
    public static Path empty(Path dir, String name) throws IOException {
        Path path = dir.resolve(name);
        Files.createFile(path);
        return path;
    }

    /** PDF magic が無い (テキストファイルだが拡張子は .pdf) */
    public static Path corruptedHeader(Path dir, String name) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, "this is not a pdf");
        return path;
    }

    private static void writeNumberedPage(PDDocument doc, PDRectangle size, int n, int total)
            throws IOException {
        PDPage page = new PDPage(size);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 48);
            cs.newLineAtOffset(80, size.getHeight() / 2);
            cs.showText("Page " + n + " / " + total);
            cs.endText();
        }
    }
}
