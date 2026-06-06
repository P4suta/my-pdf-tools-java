package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Parity;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the corpus-wide "did the columns collapse onto the grid?" overlay. For each parity the
 * detected column box of every registered page is overlaid twice: BEFORE in raw page coordinates
 * (the scan-jitter cloud) and AFTER in canvas coordinates (where the page actually landed). A tight
 * AFTER stack around the median grid box is the visible proof that registration worked, where the
 * BEFORE cloud was wide.
 *
 * <p>The AFTER box for a page is the column mapped through the placement the renderer applied
 * ({@code placedX + round(columnEdge * scale)} — see {@link Residuals}), so it is exactly where the
 * column sits on the output. The orange reference is the median of those placed boxes — the grid
 * the pages converged on — which, unlike the raw detection-space reference, stays correct when
 * pages were scaled. Pure rendering — no I/O.
 */
final class CorpusOverlayRenderer {

    private static final int CELL_W = 460;
    private static final int CELL_H = 640;
    private static final int GAP = 18;
    private static final int MARGIN = 16;
    private static final int LABEL_W = 64;
    private static final int HEADER_H = 64;
    private static final int CAPTION_H = 30;

    private static final Color RECTO = new Color(0, 150, 0);
    private static final Color VERSO = new Color(40, 90, 220);
    private static final Color GRID_REF = new Color(255, 140, 0);
    private static final Color FRAME = new Color(200, 200, 200);
    private static final Color TEXT = new Color(20, 20, 20);

    private CorpusOverlayRenderer() {}

    /**
     * Render the before/after overlay for a whole run.
     *
     * @param pages every page's recorded diagnostic, in any order
     */
    static BufferedImage render(List<PageDiagnostic> pages) {
        int width = MARGIN + LABEL_W + CELL_W + GAP + CELL_W + MARGIN;
        int height = MARGIN + HEADER_H + CELL_H + GAP + CELL_H + CAPTION_H + MARGIN;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            List<PageDiagnostic> recto = registered(pages, Parity.RECTO);
            List<PageDiagnostic> verso = registered(pages, Parity.VERSO);

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString("registration: detected column, before vs after", MARGIN, MARGIN + 18);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(
                    String.format(
                            Locale.ROOT,
                            "registered pages — recto: %d   verso: %d"
                                    + "   (outliers and undetected pages omitted)",
                            recto.size(),
                            verso.size()),
                    MARGIN,
                    MARGIN + 38);

            int colBeforeX = MARGIN + LABEL_W;
            int colAfterX = colBeforeX + CELL_W + GAP;
            int rowRectoY = MARGIN + HEADER_H;
            int rowVersoY = rowRectoY + CELL_H + GAP;

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString("BEFORE — page space (scan jitter)", colBeforeX, rowRectoY - 8);
            g.drawString("AFTER — canvas space (collapsed onto grid)", colAfterX, rowRectoY - 8);
            drawRowLabel(g, "RECTO", MARGIN, rowRectoY, RECTO);
            drawRowLabel(g, "VERSO", MARGIN, rowVersoY, VERSO);

            drawBeforeCell(g, colBeforeX, rowRectoY, recto, RECTO);
            drawAfterCell(g, colAfterX, rowRectoY, recto, RECTO);
            drawBeforeCell(g, colBeforeX, rowVersoY, verso, VERSO);
            drawAfterCell(g, colAfterX, rowVersoY, verso, VERSO);

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(
                    "grey = page/canvas frame    colored = each page's column"
                            + "    orange = median grid box (AFTER)",
                    MARGIN,
                    height - MARGIN);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void drawRowLabel(Graphics2D g, String label, int x, int y, Color color) {
        g.setColor(color);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(label, x, y + CELL_H / 2);
    }

    private static void drawBeforeCell(
            Graphics2D g, int x0, int y0, List<PageDiagnostic> pages, Color color) {
        if (pages.isEmpty()) {
            drawEmpty(g, x0, y0);
            return;
        }
        int frameW = 1;
        int frameH = 1;
        for (PageDiagnostic p : pages) {
            frameW = Math.max(frameW, p.workWidth());
            frameH = Math.max(frameH, p.workHeight());
        }
        double f = fit(frameW, frameH);
        drawFrame(g, x0, y0, frameW, frameH, f);
        g.setColor(alpha(color, 70));
        g.setStroke(new BasicStroke(1f));
        for (PageDiagnostic p : pages) {
            Box c = column(p);
            drawBox(g, x0, y0, c, f);
        }
    }

    private static void drawAfterCell(
            Graphics2D g, int x0, int y0, List<PageDiagnostic> pages, Color color) {
        if (pages.isEmpty()) {
            drawEmpty(g, x0, y0);
            return;
        }
        int canvasW = pages.get(0).canvasWidth();
        int canvasH = pages.get(0).canvasHeight();
        double f = fit(canvasW, canvasH);
        drawFrame(g, x0, y0, canvasW, canvasH, f);
        g.setColor(alpha(color, 70));
        g.setStroke(new BasicStroke(1f));
        List<Box> placed = new ArrayList<>();
        for (PageDiagnostic p : pages) {
            Box b = placedColumn(p);
            placed.add(b);
            drawBox(g, x0, y0, b, f);
        }
        Box median = medianBox(placed);
        g.setColor(GRID_REF);
        g.setStroke(new BasicStroke(2.5f));
        drawBox(g, x0, y0, median, f);
    }

    /** A registered page's column where it lands on the canvas, matching {@code PageRenderer}. */
    private static Box placedColumn(PageDiagnostic p) {
        Box c = column(p);
        double s = p.placement().scale();
        int x = p.placement().placedX() + scaled(c.x(), s);
        int y = p.placement().placedY() + scaled(c.y(), s);
        int w = Math.max(1, scaled(c.w(), s));
        int h = Math.max(1, scaled(c.h(), s));
        return new Box(x, y, w, h);
    }

    private static Box medianBox(List<Box> boxes) {
        int[] xs = boxes.stream().mapToInt(Box::x).toArray();
        int[] ys = boxes.stream().mapToInt(Box::y).toArray();
        int[] ws = boxes.stream().mapToInt(Box::w).toArray();
        int[] hs = boxes.stream().mapToInt(Box::h).toArray();
        return new Box(
                Residuals.median(xs),
                Residuals.median(ys),
                Math.max(1, Residuals.median(ws)),
                Math.max(1, Residuals.median(hs)));
    }

    private static Box column(PageDiagnostic p) {
        PageDiagnostic.Column c = p.column();
        if (c == null) {
            throw new IllegalStateException("registered page without a column: " + p.source());
        }
        return c.box();
    }

    private static List<PageDiagnostic> registered(List<PageDiagnostic> pages, Parity parity) {
        List<PageDiagnostic> out = new ArrayList<>();
        for (PageDiagnostic p : pages) {
            if (Residuals.isRegistered(p) && p.parity() == parity) {
                out.add(p);
            }
        }
        return out;
    }

    private static double fit(int frameW, int frameH) {
        return Math.min((double) CELL_W / frameW, (double) CELL_H / frameH);
    }

    private static void drawFrame(Graphics2D g, int x0, int y0, int frameW, int frameH, double f) {
        g.setColor(FRAME);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x0, y0, (int) Math.round(frameW * f), (int) Math.round(frameH * f));
    }

    private static void drawBox(Graphics2D g, int x0, int y0, Box b, double f) {
        g.drawRect(
                x0 + (int) Math.round(b.x() * f),
                y0 + (int) Math.round(b.y() * f),
                Math.max(1, (int) Math.round(b.w() * f)),
                Math.max(1, (int) Math.round(b.h() * f)));
    }

    private static void drawEmpty(Graphics2D g, int x0, int y0) {
        g.setColor(FRAME);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x0, y0, CELL_W, CELL_H);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("(no registered pages)", x0 + 12, y0 + 24);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }
}
