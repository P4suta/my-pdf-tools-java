package io.github.p4suta.register.infrastructure.diag;

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
import java.util.function.ToIntFunction;

/**
 * Plots each registered page's residual — the distance from its placed column edge to the type-area
 * grid edge — against page index, one panel for the horizontal (x) residual and one for the
 * vertical (y). Recto and verso are distinguished by color and marker. A low, flat point cloud
 * means tight registration; a spike names the page that drifted. The residual comes from {@link
 * Residuals}, the same source the summary text uses, so the chart and {@code summary.txt} always
 * agree. Pure rendering — no I/O.
 */
final class ResidualChartRenderer {

    private static final int WIDTH = 900;
    private static final int PANEL_H = 280;
    private static final int MARGIN = 50;
    private static final int GAP = 50;
    private static final int X_LABEL_H = 30;
    private static final int TITLE_H = 22;
    private static final int MIN_Y_SPAN = 8;

    private static final Color RECTO = new Color(0, 150, 0);
    private static final Color VERSO = new Color(40, 90, 220);
    private static final Color AXIS = new Color(120, 120, 120);
    private static final Color GRIDLINE = new Color(225, 225, 225);
    private static final Color TEXT = new Color(20, 20, 20);

    private ResidualChartRenderer() {}

    /**
     * Render the residual scatter for a whole run.
     *
     * @param pages every page's recorded diagnostic, in any order
     * @return the chart image
     */
    static BufferedImage render(List<PageDiagnostic> pages) {
        List<PageDiagnostic> reg = new ArrayList<>();
        int maxIndex = 0;
        for (PageDiagnostic p : pages) {
            if (Residuals.isRegistered(p)) {
                reg.add(p);
                maxIndex = Math.max(maxIndex, p.index());
            }
        }
        int height = MARGIN + PANEL_H + GAP + PANEL_H + MARGIN;
        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, height);
            if (reg.isEmpty()) {
                g.setColor(TEXT);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                g.drawString("no registered pages to chart", MARGIN, MARGIN + 20);
                return img;
            }
            drawPanel(
                    g,
                    MARGIN,
                    "horizontal residual — distance to left/right grid edge (px)",
                    reg,
                    maxIndex,
                    Residuals::horizontal);
            drawPanel(
                    g,
                    MARGIN + PANEL_H + GAP,
                    "vertical residual — distance to top/bottom grid edge (px)",
                    reg,
                    maxIndex,
                    Residuals::vertical);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void drawPanel(
            Graphics2D g,
            int top,
            String title,
            List<PageDiagnostic> reg,
            int maxIndex,
            ToIntFunction<PageDiagnostic> residual) {
        int left = MARGIN;
        int right = WIDTH - MARGIN;
        int plotTop = top + TITLE_H;
        int bottom = top + PANEL_H - X_LABEL_H;
        int maxY = MIN_Y_SPAN;
        for (PageDiagnostic p : reg) {
            maxY = Math.max(maxY, residual.applyAsInt(p));
        }

        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(title, left, top + 14);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int k = 0; k <= 2; k++) {
            int val = (int) Math.round(maxY * (k / 2.0));
            int yy = mapY(val, maxY, plotTop, bottom);
            g.setColor(GRIDLINE);
            g.drawLine(left, yy, right, yy);
            g.setColor(TEXT);
            g.drawString(String.valueOf(val), left - 30, yy + 4);
        }

        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(left, plotTop, left, bottom);
        g.drawLine(left, bottom, right, bottom);
        g.setColor(TEXT);
        g.drawString("page index →", right - 90, bottom + 22);

        for (PageDiagnostic p : reg) {
            int v = residual.applyAsInt(p);
            int px = mapX(p.index(), maxIndex, left, right);
            int py = mapY(v, maxY, plotTop, bottom);
            if (p.parity() == Parity.RECTO) {
                g.setColor(alpha(RECTO, 200));
                g.fillOval(px - 3, py - 3, 6, 6);
            } else {
                g.setColor(alpha(VERSO, 200));
                fillTriangle(g, px, py, 4);
            }
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(RECTO);
        g.fillOval(right - 150, plotTop + 2, 6, 6);
        g.setColor(TEXT);
        g.drawString("recto", right - 140, plotTop + 9);
        g.setColor(VERSO);
        fillTriangle(g, right - 67, plotTop + 6, 4);
        g.setColor(TEXT);
        g.drawString("verso", right - 60, plotTop + 9);
    }

    private static int mapX(int index, int maxIndex, int left, int right) {
        if (maxIndex <= 0) {
            return left + (right - left) / 2;
        }
        return left + (int) Math.round((double) index / maxIndex * (right - left));
    }

    private static int mapY(int value, int maxY, int plotTop, int bottom) {
        return bottom - (int) Math.round((double) value / maxY * (bottom - plotTop));
    }

    private static void fillTriangle(Graphics2D g, int cx, int cy, int r) {
        int[] xs = {cx, cx - r, cx + r};
        int[] ys = {cy - r, cy + r, cy + r};
        g.fillPolygon(xs, ys, 3);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
