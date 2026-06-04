package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Skew;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Draws one page's diagnostic overlay with the Java 2D API: the (downscaled) page with its detected
 * column, vertical band and reference box marked, the row- and column-ink projection profiles
 * plotted alongside, and a text panel of the numeric state. Pure rendering — no I/O.
 */
final class DiagnosticRenderer {

    private static final int VIEW_MAX = 1100;
    private static final int PLOT_W = 180;
    private static final int PLOT_H = 150;
    private static final int GAP = 14;
    private static final int MARGIN = 14;
    private static final int TEXT_H = 170;
    private static final int LINE_H = 19;

    private static final Color COLUMN = new Color(0, 160, 0);
    private static final Color BAND = new Color(40, 110, 255);
    private static final Color REFERENCE = new Color(255, 140, 0);
    private static final Color PROFILE = new Color(110, 110, 110);
    private static final Color GRID = new Color(220, 220, 220);
    private static final Color TEXT = new Color(20, 20, 20);

    private DiagnosticRenderer() {}

    /**
     * Render the overlay for one page.
     *
     * @param page the (deskewed) page as an RGB image
     * @param d the page's recorded diagnostic
     * @param rowInk the whole-page row-ink profile (length = page height)
     * @param columnInk the in-band column-ink profile (length = page width)
     * @return the composited diagnostic image
     */
    static BufferedImage render(
            BufferedImage page, PageDiagnostic d, int[] rowInk, int[] columnInk) {
        int workW = d.workWidth();
        int workH = d.workHeight();
        double s = Math.min(1.0, (double) VIEW_MAX / Math.max(workW, workH));
        int viewW = Math.max(1, (int) Math.round(workW * s));
        int viewH = Math.max(1, (int) Math.round(workH * s));

        int width = MARGIN + viewW + GAP + PLOT_W + MARGIN;
        int height = MARGIN + viewH + GAP + PLOT_H + GAP + TEXT_H + MARGIN;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            int vx0 = MARGIN;
            int vy0 = MARGIN;
            g.drawImage(page, vx0, vy0, viewW, viewH, null);
            g.setColor(GRID);
            g.drawRect(vx0, vy0, viewW - 1, viewH - 1);

            PageDiagnostic.Column col = d.column();
            if (col != null) {
                int by = vy0 + (int) Math.round(col.bandStart() * s);
                int bh = Math.max(1, (int) Math.round((col.bandEnd() - col.bandStart()) * s));
                g.setColor(new Color(BAND.getRed(), BAND.getGreen(), BAND.getBlue(), 40));
                g.fillRect(vx0, by, viewW, bh);
                g.setColor(BAND);
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(vx0, by, vx0 + viewW, by);
                g.drawLine(vx0, by + bh, vx0 + viewW, by + bh);
            }
            Box ref = d.referenceBox();
            if (ref != null) {
                g.setColor(REFERENCE);
                g.setStroke(new BasicStroke(2f));
                drawBox(g, ref, vx0, vy0, s);
            }
            if (col != null) {
                g.setColor(COLUMN);
                g.setStroke(new BasicStroke(2.5f));
                drawBox(g, col.box(), vx0, vy0, s);
            }

            drawRowProfile(g, rowInk, MARGIN + viewW + GAP, MARGIN, PLOT_W, viewH, s, col);
            drawColumnProfile(g, columnInk, MARGIN, MARGIN + viewH + GAP, viewW, PLOT_H, s, col);
            drawText(g, d, MARGIN, MARGIN + viewH + GAP + PLOT_H + GAP + LINE_H);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void drawBox(Graphics2D g, Box b, int x0, int y0, double s) {
        g.drawRect(
                x0 + (int) Math.round(b.x() * s),
                y0 + (int) Math.round(b.y() * s),
                Math.max(1, (int) Math.round(b.w() * s)),
                Math.max(1, (int) Math.round(b.h() * s)));
    }

    private static void drawRowProfile(
            Graphics2D g,
            int[] ink,
            int x0,
            int y0,
            int w,
            int h,
            double s,
            PageDiagnostic.@Nullable Column col) {
        g.setColor(GRID);
        g.drawRect(x0, y0, w - 1, h - 1);
        int max = maxOf(ink);
        if (max <= 0) {
            return;
        }
        if (col != null) {
            int by = y0 + (int) Math.round(col.bandStart() * s);
            int bh = Math.max(1, (int) Math.round((col.bandEnd() - col.bandStart()) * s));
            g.setColor(new Color(BAND.getRed(), BAND.getGreen(), BAND.getBlue(), 30));
            g.fillRect(x0, by, w, bh);
        }
        g.setColor(PROFILE);
        int n = ink.length;
        for (int yy = 0; yy < h; yy++) {
            int idx = Math.min(n - 1, (int) (yy / s));
            int bar = (int) Math.round((double) ink[idx] / max * (w - 2));
            if (bar > 0) {
                g.drawLine(x0 + 1, y0 + yy, x0 + 1 + bar, y0 + yy);
            }
        }
    }

    private static void drawColumnProfile(
            Graphics2D g,
            int[] ink,
            int x0,
            int y0,
            int w,
            int h,
            double s,
            PageDiagnostic.@Nullable Column col) {
        g.setColor(GRID);
        g.drawRect(x0, y0, w - 1, h - 1);
        int max = maxOf(ink);
        if (max <= 0) {
            return;
        }
        if (col != null) {
            Box b = col.box();
            int bx = x0 + (int) Math.round(b.x() * s);
            int bw = Math.max(1, (int) Math.round(b.w() * s));
            g.setColor(new Color(COLUMN.getRed(), COLUMN.getGreen(), COLUMN.getBlue(), 30));
            g.fillRect(bx, y0, bw, h);
        }
        g.setColor(PROFILE);
        int n = ink.length;
        for (int xx = 0; xx < w; xx++) {
            int idx = Math.min(n - 1, (int) (xx / s));
            int bar = (int) Math.round((double) ink[idx] / max * (h - 2));
            if (bar > 0) {
                g.drawLine(x0 + xx, y0 + h - 1, x0 + xx, y0 + h - 1 - bar);
            }
        }
    }

    private static void drawText(Graphics2D g, PageDiagnostic d, int x, int y) {
        g.setColor(TEXT);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int yy = y;
        for (String s : textLines(d)) {
            g.drawString(s, x, yy);
            yy += LINE_H;
        }
    }

    private static List<String> textLines(PageDiagnostic d) {
        List<String> out = new ArrayList<>();
        out.add(
                String.format(
                        Locale.ROOT,
                        "%s  [%s]   work %dx%d  ->  canvas %dx%d",
                        d.source(),
                        d.parity(),
                        d.workWidth(),
                        d.workHeight(),
                        d.canvasWidth(),
                        d.canvasHeight()));
        Skew sk = d.skew();
        out.add(
                sk == null
                        ? "skew: (deskew off)"
                        : String.format(
                                Locale.ROOT,
                                "skew: angle=%+.3f deg  conf=%.2f  found=%b  applied=%b",
                                sk.angleDeg(),
                                sk.conf(),
                                sk.found(),
                                sk.applied()));
        PageDiagnostic.Column col = d.column();
        out.add(
                col == null
                        ? "column: (none detected)"
                        : String.format(
                                Locale.ROOT,
                                "column: x=%d y=%d w=%d h=%d   band=[%d,%d)",
                                col.box().x(),
                                col.box().y(),
                                col.box().w(),
                                col.box().h(),
                                col.bandStart(),
                                col.bandEnd()));
        Box ref = d.referenceBox();
        out.add(
                "reference: "
                        + (ref == null
                                ? "(none)"
                                : String.format(
                                        Locale.ROOT,
                                        "x=%d y=%d w=%d h=%d",
                                        ref.x(),
                                        ref.y(),
                                        ref.w(),
                                        ref.h())));
        PageDiagnostic.Placement pl = d.placement();
        out.add(
                String.format(
                        Locale.ROOT,
                        "transform: scale=%.4f  intended dx=%d dy=%d  passthrough=%b",
                        pl.scale(),
                        pl.intendedDx(),
                        pl.intendedDy(),
                        pl.passthrough()));
        out.add(
                String.format(
                        Locale.ROOT,
                        "placed: x=%d y=%d  croppedX=%b croppedY=%b  content %dx%d",
                        pl.placedX(),
                        pl.placedY(),
                        pl.croppedX(),
                        pl.croppedY(),
                        pl.contentWidth(),
                        pl.contentHeight()));
        out.add("legend: green=column  blue=band  orange=reference  grey=ink profile");
        return out;
    }

    private static int maxOf(int[] a) {
        int max = 0;
        for (int v : a) {
            max = Math.max(max, v);
        }
        return max;
    }
}
