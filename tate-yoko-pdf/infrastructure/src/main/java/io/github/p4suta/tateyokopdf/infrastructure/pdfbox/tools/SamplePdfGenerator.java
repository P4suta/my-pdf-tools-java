package io.github.p4suta.tateyokopdf.infrastructure.pdfbox.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class SamplePdfGenerator {

    private SamplePdfGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: SamplePdfGenerator <output.pdf> [pages]");
            System.exit(1);
        }
        Path output = Path.of(args[0]);
        int pages = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 48);
                    cs.newLineAtOffset(200, 400);
                    cs.showText("Page " + i + " / " + pages);
                    cs.endText();
                }
            }
            doc.save(output.toFile());
        }
        System.out.println("Wrote " + pages + " pages to " + output);
    }
}
