package io.github.p4suta.despeckle.infrastructure.tools;

import io.github.p4suta.despeckle.domain.model.OutputFormat;
import io.github.p4suta.despeckle.domain.model.ProcessOptions;
import io.github.p4suta.despeckle.infrastructure.leptonica.LeptonicaPageCleaner;
import io.github.p4suta.despeckle.testsupport.TestImages;
import io.github.p4suta.shared.imaging.Pix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Op-level micro-benchmark for the despeckle page cleaner: times each Leptonica primitive {@link
 * LeptonicaPageCleaner} composes, plus the end-to-end {@code clean()}, on a deterministic synthetic
 * 600-dpi A5 page (3496×4961 px — the pdfbook pipeline's real page geometry). Writes a Markdown
 * table to {@code despeckle/docs/cleaner-baseline.md} so every optimization claim is judged against
 * a committed measurement, mirroring the pipeline's {@code benchPipeline} convention.
 *
 * <p>Each op row reports the median and minimum of N timed reps (after warmup), the number of times
 * {@code clean()} invokes that op per page with default options, and the estimated share of the
 * end-to-end time the op therefore accounts for. The Σ(median × calls) sanity row should land near
 * the {@code clean()} median; a large gap means an untimed cost (allocation, G4 codec internals).
 *
 * <p>Test-sources tool (driven by the {@code benchCleaner} Gradle task): never ships, and needs the
 * dev container's Leptonica. Usage: {@code CleanerBenchmark <outDoc> [reps]}.
 */
public final class CleanerBenchmark {

    private static final int DPI = 600;
    private static final int WIDTH = Math.round(148f * DPI / 25.4f); // A5 portrait
    private static final int HEIGHT = Math.round(210f * DPI / 25.4f);
    private static final int WARMUP_REPS = 2;

    // The cleaner's literal Leptonica selection flags (pix.h values, see LeptonicaPageCleaner).
    private static final int CONN_8 = 8;
    private static final int IF_EITHER = 5;
    private static final int IF_GT = 2;

    // Default-derived sizes at 600 dpi: speck k=6, isolated dust 15, proximity 21, stroke 3.
    private static final int SPECK = 6;
    private static final int DUST = 15;
    private static final int PROXIMITY = 21;
    private static final int STROKE = 3;

    private CleanerBenchmark() {}

    /** One measured op: its label, how often clean() runs it, and the timed reps. */
    private record Row(String op, int callsPerClean, double medianMs, double minMs) {}

    public static void main(String[] args) throws IOException {
        Path outDoc = Path.of(args.length > 0 ? args[0] : "despeckle/docs/cleaner-baseline.md");
        int reps = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        Path work = Files.createTempDirectory("cleaner-bench");
        try {
            Path pbm = work.resolve("page.pbm");
            TestImages.writePbm(pbm, syntheticPage());
            Path g4 = work.resolve("page.tif");
            try (Pix page = Pix.read(pbm)) {
                page.setResolution(DPI);
                page.writeTiffG4(g4);
            }

            List<Row> rows = new ArrayList<>();
            try (Pix page = Pix.read(g4);
                    Pix inverted = page.inverted();
                    Pix text6 = page.selectBySize(SPECK, SPECK, CONN_8, IF_EITHER, IF_GT);
                    Pix text15 = page.selectBySize(DUST, DUST, CONN_8, IF_EITHER, IF_GT)) {

                rows.add(timePix("read TIFF-G4", 1, reps, () -> Pix.read(g4)));
                rows.add(
                        timePix(
                                "selectBySize k=6 (page)",
                                1,
                                reps,
                                () -> page.selectBySize(SPECK, SPECK, CONN_8, IF_EITHER, IF_GT)));
                rows.add(
                        timePix(
                                "selectBySize 15 (page)",
                                1,
                                reps,
                                () -> page.selectBySize(DUST, DUST, CONN_8, IF_EITHER, IF_GT)));
                rows.add(
                        timePix(
                                "selectBySize k=6 (inverted)",
                                2,
                                reps,
                                () ->
                                        inverted.selectBySize(
                                                SPECK, SPECK, CONN_8, IF_EITHER, IF_GT)));
                rows.add(
                        timePix(
                                "dilate 43x43 (text mask)",
                                1,
                                reps,
                                () -> text15.dilated(PROXIMITY)));
                rows.add(timePix("open 7x7 (page)", 1, reps, () -> page.opened(STROKE)));
                rows.add(timePix("invert", 2, reps, page::inverted));
                rows.add(timePix("subtract", 5, reps, () -> page.subtract(text6)));
                rows.add(timePix("and", 1, reps, () -> page.and(text6)));
                rows.add(timePix("or", 3, reps, () -> page.or(text6)));
                rows.add(timeVoid("countConnComp", 2, reps, page::connectedComponents));
                rows.add(timeVoid("countPixels", 2, reps, page::blackPixels));
                Path scratchTif = work.resolve("write.tif");
                rows.add(timeVoid("write TIFF-G4", 1, reps, () -> page.writeTiffG4(scratchTif)));
            }

            LeptonicaPageCleaner cleaner = new LeptonicaPageCleaner();
            Path cleaned = work.resolve("cleaned.tif");
            Row clean =
                    timeVoid(
                            "clean() end-to-end",
                            1,
                            reps,
                            () -> cleaner.clean(g4, cleaned, OutputFormat.TIFF, options()));
            // The pipeline's actual configuration (no report -> component counting skipped).
            Row cleanNoStats =
                    timeVoid(
                            "clean() without component stats",
                            1,
                            reps,
                            () ->
                                    cleaner.clean(
                                            g4,
                                            cleaned,
                                            OutputFormat.TIFF,
                                            options().withoutComponentStats()));

            String report = render(rows, clean, cleanNoStats, reps);
            Path parent = outDoc.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outDoc, report, StandardCharsets.UTF_8);
            System.out.print(report);
            System.err.println();
            System.err.println("→ wrote " + outDoc);
        } finally {
            deleteTree(work);
        }
    }

    private static ProcessOptions options() {
        // The pipeline's exact configuration: defaults (fillHoles + isolatedDust on) at a known
        // dpi, so the derived sizes match the op rows above.
        return ProcessOptions.defaults().withDpi(DPI);
    }

    // ---- timing ----

    @FunctionalInterface
    private interface PixOp {
        Pix run() throws IOException;
    }

    @FunctionalInterface
    private interface VoidOp {
        void run() throws IOException;
    }

    /** Times an op returning a Pix; the result's close() happens outside the timed window. */
    private static Row timePix(String op, int calls, int reps, PixOp body) throws IOException {
        double[] samples = new double[reps];
        for (int i = -WARMUP_REPS; i < reps; i++) {
            long start = System.nanoTime();
            Pix result = body.run();
            long elapsed = System.nanoTime() - start;
            result.close();
            if (i >= 0) {
                samples[i] = elapsed / 1e6;
            }
        }
        return new Row(op, calls, median(samples), Arrays.stream(samples).min().orElse(0));
    }

    private static Row timeVoid(String op, int calls, int reps, VoidOp body) throws IOException {
        double[] samples = new double[reps];
        for (int i = -WARMUP_REPS; i < reps; i++) {
            long start = System.nanoTime();
            body.run();
            long elapsed = System.nanoTime() - start;
            if (i >= 0) {
                samples[i] = elapsed / 1e6;
            }
        }
        return new Row(op, calls, median(samples), Arrays.stream(samples).min().orElse(0));
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    // ---- the synthetic page ----

    /**
     * A deterministic 600-dpi A5 "scan": right-to-left glyph columns (despeckle's protected text),
     * 1–3 px dust everywhere (the speck filter's work), isolated 8–12 px blots in the margins (the
     * isolated-dust pass's work), and pin-holes punched into glyph blocks (the fill-holes pass's
     * work). Fixed seed, so the baseline is reproducible.
     */
    private static boolean[][] syntheticPage() {
        boolean[][] img = TestImages.blank(WIDTH, HEIGHT);
        Random random = new Random(42);

        int margin = WIDTH / 10;
        int glyph = Math.max(4, WIDTH / 60);
        int leading = glyph / 2;
        int top = HEIGHT / 12;
        int bottom = HEIGHT - HEIGHT / 12;
        int glyphIndex = 0;
        for (int x = WIDTH - margin - glyph; x >= margin; x -= glyph + leading) {
            int y = top;
            while (y + glyph <= bottom) {
                if (random.nextInt(100) < 8) {
                    y += glyph * (2 + random.nextInt(4));
                    continue;
                }
                TestImages.fillRect(img, x, y, x + glyph - 3, y + glyph - 3);
                // Every ~40th glyph carries a pin-hole, so fillHoles has real work.
                if (glyphIndex % 40 == 0) {
                    int hx = x + glyph / 2;
                    int hy = y + glyph / 2;
                    img[hy][hx] = false;
                    img[hy][hx + 1] = false;
                    img[hy + 1][hx] = false;
                    img[hy + 1][hx + 1] = false;
                }
                glyphIndex++;
                y += glyph;
            }
        }

        // Salt-and-pepper dust (1–3 px), what the main speck filter removes.
        int specks = WIDTH * HEIGHT / 25_000;
        for (int i = 0; i < specks; i++) {
            int size = 1 + random.nextInt(3);
            int x = random.nextInt(WIDTH - size);
            int y = random.nextInt(HEIGHT - size);
            TestImages.fillRect(img, x, y, x + size - 1, y + size - 1);
        }

        // Isolated 8–12 px blots out in the margins, what the isolated-dust pass removes.
        for (int i = 0; i < 20; i++) {
            int size = 8 + random.nextInt(5);
            int x =
                    random.nextBoolean()
                            ? random.nextInt(margin - size)
                            : WIDTH - margin + random.nextInt(margin - size);
            int y = top + random.nextInt(bottom - top - size);
            TestImages.fillRect(img, x, y, x + size - 1, y + size - 1);
        }
        return img;
    }

    // ---- rendering ----

    private static String render(List<Row> rows, Row clean, Row cleanNoStats, int reps) {
        var sb = new StringBuilder();
        sb.append("# despeckle cleaner op-level baseline\n\n")
                .append("Generated by `CleanerBenchmark`")
                .append(" (`./gradlew :despeckle:infrastructure:benchCleaner`, dev container).\n")
                .append("Times each Leptonica primitive the page cleaner composes on a synthetic\n")
                .append("600-dpi A5 page (")
                .append(WIDTH)
                .append("x")
                .append(HEIGHT)
                .append(" px, fixed seed). Re-run after any change to the\n")
                .append("cleaner or the imaging bindings and compare before merging.\n\n");
        String date =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
        sb.append("- Date (UTC): ").append(date).append('\n');
        sb.append("- Host: ")
                .append(System.getProperty("os.name", "?"))
                .append(' ')
                .append(System.getProperty("os.arch", "?"))
                .append(", ")
                .append(Runtime.getRuntime().availableProcessors())
                .append(" CPUs\n");
        sb.append("- Samples: median of ")
                .append(reps)
                .append(" reps after ")
                .append(WARMUP_REPS)
                .append(" warmups; single-threaded.\n\n");

        sb.append("| op | median (ms) | min (ms) | calls/clean() | est. share of clean() |\n");
        sb.append("|---|---:|---:|---:|---:|\n");
        double estimatedTotal = 0;
        for (Row row : rows) {
            double estimated = row.medianMs() * row.callsPerClean();
            estimatedTotal += estimated;
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %.2f | %.2f | %d | %.1f%% |%n",
                            row.op(),
                            row.medianMs(),
                            row.minMs(),
                            row.callsPerClean(),
                            estimated * 100.0 / clean.medianMs()));
        }
        sb.append(
                String.format(
                        Locale.ROOT,
                        "| **Σ(median × calls)** | %.2f | | | %.1f%% |%n",
                        estimatedTotal,
                        estimatedTotal * 100.0 / clean.medianMs()));
        sb.append(
                String.format(
                        Locale.ROOT,
                        "| **clean() end-to-end** | %.2f | %.2f | 1 | 100%% |%n",
                        clean.medianMs(),
                        clean.minMs()));
        sb.append(
                String.format(
                        Locale.ROOT,
                        "| **clean() without component stats** | %.2f | %.2f | 1 | %.1f%% |%n",
                        cleanNoStats.medianMs(),
                        cleanNoStats.minMs(),
                        cleanNoStats.medianMs() * 100.0 / clean.medianMs()));
        sb.append(
                        "\nThe Σ row landing near 100% means the table accounts for clean()'s real"
                                + " cost;\n")
                .append(
                        "a large gap points at untimed work (allocation churn, codec"
                                + " internals).\n");
        return sb.toString();
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    System.err.println(
                                            "warn: could not delete " + p + ": " + e.getMessage());
                                }
                            });
        }
    }
}
