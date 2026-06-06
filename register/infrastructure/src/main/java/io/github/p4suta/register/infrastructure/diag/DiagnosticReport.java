package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.PageDiagnostic;
import io.github.p4suta.register.domain.model.Parity;
import io.github.p4suta.register.domain.model.RunInfo;
import io.github.p4suta.register.domain.model.Skew;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/** Renders the diagnostic JSONL log (one object per page) and the human-readable run summary. */
final class DiagnosticReport {

    private DiagnosticReport() {}

    /** Write one JSON object per page, in reading order, to {@code file}. */
    static void writeJsonl(Path file, List<PageDiagnostic> pages) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (PageDiagnostic p : pages) {
            sb.append(toJson(p)).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    /** Write the aggregate summary to {@code file}. */
    static void writeSummary(Path file, RunInfo run, List<PageDiagnostic> pages)
            throws IOException {
        Files.writeString(file, summary(run, pages), StandardCharsets.UTF_8);
    }

    // JSONL

    private static String toJson(PageDiagnostic p) {
        return new Json()
                .field("index", p.index())
                .field("parity", p.parity().name())
                .field("source", p.source())
                .field("workWidth", p.workWidth())
                .field("workHeight", p.workHeight())
                .field("canvasWidth", p.canvasWidth())
                .field("canvasHeight", p.canvasHeight())
                .field("deskewEnabled", p.deskewEnabled())
                .fieldRaw("skew", skewJson(p.skew()))
                .fieldRaw("column", columnJson(p.column()))
                .fieldRaw("referenceBox", boxJson(p.referenceBox()))
                .fieldRaw("placement", placementJson(p.placement()))
                .end();
    }

    private static @Nullable String skewJson(@Nullable Skew s) {
        if (s == null) {
            return null;
        }
        return new Json()
                .field("angleDeg", s.angleDeg())
                .field("conf", s.conf())
                .field("found", s.found())
                .field("applied", s.applied())
                .end();
    }

    private static @Nullable String columnJson(PageDiagnostic.@Nullable Column c) {
        if (c == null) {
            return null;
        }
        return new Json()
                .fieldRaw("box", boxJson(c.box()))
                .field("bandStart", c.bandStart())
                .field("bandEnd", c.bandEnd())
                .end();
    }

    private static @Nullable String boxJson(@Nullable Box b) {
        if (b == null) {
            return null;
        }
        return new Json()
                .field("x", b.x())
                .field("y", b.y())
                .field("w", b.w())
                .field("h", b.h())
                .end();
    }

    private static String placementJson(PageDiagnostic.Placement pl) {
        return new Json()
                .field("detected", pl.detected())
                .field("passthrough", pl.passthrough())
                .field("scale", pl.scale())
                .field("intendedDx", pl.intendedDx())
                .field("intendedDy", pl.intendedDy())
                .field("placedX", pl.placedX())
                .field("placedY", pl.placedY())
                .field("croppedX", pl.croppedX())
                .field("croppedY", pl.croppedY())
                .field("contentWidth", pl.contentWidth())
                .field("contentHeight", pl.contentHeight())
                .end();
    }

    // summary

    private static String summary(RunInfo run, List<PageDiagnostic> pages) {
        StringBuilder sb = new StringBuilder();
        line(sb, "register diagnostics — summary");
        line(sb, "==============================");
        line(
                sb,
                String.format(
                        Locale.ROOT,
                        "paper: %s   dpi: %d   canvas: %dx%d px",
                        run.paper(),
                        run.dpi(),
                        run.canvasWidth(),
                        run.canvasHeight()));
        line(
                sb,
                String.format(
                        Locale.ROOT,
                        "deskew: %s   anchor: %s   outlier-ratio: %.3f",
                        run.deskewEnabled() ? "on" : "off",
                        run.anchor(),
                        run.outlierRatio()));
        long centered = pages.stream().filter(p -> !p.placement().detected()).count();
        line(
                sb,
                String.format(
                        Locale.ROOT,
                        "pages: %d   analyzed (column detected): %d   centered (no column): %d",
                        run.totalPages(),
                        run.analyzedPages(),
                        centered));
        blank(sb);

        line(sb, "reference box (recto): " + boxText(run.referenceRecto()));
        line(sb, "reference box (verso): " + boxText(run.referenceVerso()));
        blank(sb);

        List<Box> cols =
                pages.stream()
                        .map(PageDiagnostic::column)
                        .filter(Objects::nonNull)
                        .map(PageDiagnostic.Column::box)
                        .toList();
        line(sb, String.format(Locale.ROOT, "detected column geometry (n=%d):", cols.size()));
        if (!cols.isEmpty()) {
            line(sb, "  x: " + stat(cols, Box::x));
            line(sb, "  y: " + stat(cols, Box::y));
            line(sb, "  w: " + stat(cols, Box::w));
            line(sb, "  h: " + stat(cols, Box::h));
        }
        blank(sb);

        List<Double> rotated = new ArrayList<>();
        int found = 0;
        for (PageDiagnostic p : pages) {
            Skew s = p.skew();
            if (s != null && s.found()) {
                found++;
            }
            if (s != null && s.applied()) {
                rotated.add(Math.abs(s.angleDeg()));
            }
        }
        if (run.deskewEnabled()) {
            line(
                    sb,
                    String.format(
                            Locale.ROOT,
                            "deskew: rotated %d / %d page(s) (skew found on %d)",
                            rotated.size(),
                            run.totalPages(),
                            found));
            if (!rotated.isEmpty()) {
                line(sb, "  |angle| when rotated (deg): " + statD(rotated));
            }
        } else {
            line(sb, "deskew: off");
        }
        blank(sb);

        registrationConsistency(sb, pages);
        blank(sb);

        line(
                sb,
                listSection(
                        "outliers (centered, not registered)",
                        pages,
                        p -> p.placement().passthrough()));
        line(
                sb,
                listSection(
                        "cropped to canvas (scan margin overflow trimmed)",
                        pages,
                        p -> p.placement().croppedX() || p.placement().croppedY()));
        line(
                sb,
                listSection(
                        "no column detected (centered as-is)",
                        pages,
                        p -> !p.placement().detected()));
        return sb.toString();
    }

    /**
     * How far each registered page's column lands from the type-area grid — the direct measure of
     * registration quality. For each axis a page is anchored to whichever grid edge it sits on
     * (right or left, top or bottom), so the residual is the distance from the column's nearer edge
     * to that reference edge: near-zero means the text block is on the grid (so the page number
     * riding with it lands consistently too), a large residual means it drifted. Reported per
     * parity, since recto and verso have their own references. Outliers and centered pages are
     * excluded — they are not registered to the grid.
     */
    private static void registrationConsistency(StringBuilder sb, List<PageDiagnostic> pages) {
        line(sb, "registered column distance to the type-area grid edge (lower = tighter):");
        for (Parity parity : Parity.values()) {
            consistencyForParity(sb, pages, parity);
        }
    }

    private static void consistencyForParity(
            StringBuilder sb, List<PageDiagnostic> pages, Parity parity) {
        List<PageDiagnostic> reg =
                pages.stream()
                        .filter(Residuals::isRegistered)
                        .filter(p -> p.parity() == parity)
                        .toList();
        if (reg.isEmpty()) {
            line(sb, String.format(Locale.ROOT, "  %-5s n=0", parity));
            return;
        }
        int[] horizontal = reg.stream().mapToInt(Residuals::horizontal).toArray();
        int[] vertical = reg.stream().mapToInt(Residuals::vertical).toArray();
        line(
                sb,
                String.format(
                        Locale.ROOT,
                        "  %-5s n=%d  x (left/right edge) %s   y (top/bottom edge) %s",
                        parity,
                        reg.size(),
                        residual(horizontal),
                        residual(vertical)));
    }

    private static String residual(int[] values) {
        return String.format(
                Locale.ROOT,
                "median=%d  max=%d px",
                Residuals.median(values),
                Residuals.max(values));
    }

    private static String stat(List<Box> boxes, ToIntFunction<Box> component) {
        int[] v = boxes.stream().mapToInt(component).sorted().toArray();
        return String.format(
                Locale.ROOT, "min=%d  median=%d  max=%d", v[0], v[v.length / 2], v[v.length - 1]);
    }

    private static String statD(List<Double> values) {
        double[] v = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return String.format(
                Locale.ROOT,
                "min=%.3f  median=%.3f  max=%.3f",
                v[0],
                v[v.length / 2],
                v[v.length - 1]);
    }

    private static String listSection(
            String title, List<PageDiagnostic> pages, Predicate<PageDiagnostic> pred) {
        List<String> names = pages.stream().filter(pred).map(PageDiagnostic::source).toList();
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(": ").append(names.size()).append(" page(s)");
        if (!names.isEmpty()) {
            sb.append("\n  ").append(String.join(", ", names));
        }
        return sb.toString();
    }

    private static String boxText(@Nullable Box b) {
        return b == null
                ? "(none)"
                : String.format(Locale.ROOT, "x=%d y=%d w=%d h=%d", b.x(), b.y(), b.w(), b.h());
    }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append('\n');
    }

    private static void blank(StringBuilder sb) {
        sb.append('\n');
    }
}
