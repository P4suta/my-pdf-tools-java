package io.github.p4suta.pipeline.tools;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage-level runtime + memory benchmark for the installed {@code pdfbook} launcher — the pdfbook
 * counterpart of tate's {@code RuntimeBenchmark}, with per-stage attribution as the addition.
 *
 * <p>Drives {@code pdfbook <in> -o <tmp>/out.pdf --force --timings [-j N]} as a child process,
 * measuring end-to-end wall around the process ({@link System#nanoTime()}), peak RSS by sampling
 * the child's {@code /proc/<pid>/status} {@code VmHWM} (Linux-only; {@code n/a} elsewhere), and the
 * per-stage wall by parsing the stable {@code timing: <stage> = <seconds>s} lines {@code
 * StageTimingSink} prints. Writes a Markdown report.
 *
 * <p>Test-sources tool (driven by the {@code benchPipeline} Gradle task): it never ships in the
 * production launcher, and it expects the dev container's native toolchain (pdfimages, Leptonica,
 * qpdf) on PATH — the installDist launcher, not the jpackage image, is what it measures.
 *
 * <p>Usage: {@code PipelineBenchmark <launcher> <qpdf> <outDoc> <runs> <jobsCsv|auto>
 * <input.pdf>...} — {@code jobsCsv} is a comma-separated {@code -j} sweep ({@code auto} = omit
 * {@code -j}, i.e. the launcher's CPU-count default).
 */
public final class PipelineBenchmark {

    private static final Pattern TIMING =
            Pattern.compile("^timing: (\\S+) = ([0-9.]+)s", Pattern.MULTILINE);
    private static final Pattern VM_HWM = Pattern.compile("VmHWM:\\s*([0-9]+)");
    private static final long POLL_MILLIS = 5;
    private static final long PROCESS_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(30);
    private static final long MIB = 1024L * 1024L;

    private final Path launcher;
    private final String qpdf;
    private final Path outDoc;
    private final int runs;
    private final List<String> jobsSweep;

    private PipelineBenchmark(
            Path launcher, String qpdf, Path outDoc, int runs, List<String> jobsSweep) {
        this.launcher = launcher;
        this.qpdf = qpdf;
        this.outDoc = outDoc;
        this.runs = runs;
        this.jobsSweep = jobsSweep;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 6) {
            System.err.println(
                    "usage: PipelineBenchmark <launcher> <qpdf> <outDoc> <runs> <jobsCsv|auto>"
                            + " <input.pdf>...");
            System.exit(2);
            return;
        }
        var benchmark =
                new PipelineBenchmark(
                        Path.of(args[0]),
                        args[1],
                        Path.of(args[2]),
                        Integer.parseInt(args[3]),
                        Arrays.stream(args[4].split(",")).map(String::trim).toList());
        List<Path> inputs = Arrays.stream(args).skip(5).map(Path::of).toList();
        benchmark.run(inputs);
    }

    // Result records

    /** One measured child run: wall seconds, peak RSS (KiB, -1 if unavailable), merged output. */
    private record Timed(double elapsedSeconds, long maxRssKib, String output) {}

    /** A finished input × jobs measurement, ready to render. */
    private record Row(
            String name,
            String jobs,
            int pages,
            long inputBytes,
            double wallMedian,
            double coldWall,
            Map<String, Double> stageMedians,
            long rssMedianKib,
            long outputBytes) {}

    // Orchestration

    private void run(List<Path> inputs) throws IOException, InterruptedException {
        requireExecutable(launcher, "pdfbook launcher", "build it first:  just pdfbook-install");

        List<Row> rows = new ArrayList<>();
        for (Path input : inputs) {
            if (!Files.isRegularFile(input)) {
                System.err.println("skip (not found): " + input);
                continue;
            }
            for (String jobs : jobsSweep) {
                rows.add(measure(input, jobs));
            }
        }

        String report = render(rows);
        Files.createDirectories(requireParent(outDoc));
        Files.writeString(outDoc, report, StandardCharsets.UTF_8);
        System.out.print(report);
        System.err.println();
        System.err.println("→ wrote " + outDoc);
    }

    private Row measure(Path input, String jobs) throws IOException, InterruptedException {
        int pages = pageCount(input);
        long inputBytes = Files.size(input);
        System.err.printf(
                Locale.ROOT,
                "Measuring: %s (%dp, %s MiB, jobs=%s)…%n",
                fileName(input),
                pages,
                mib(inputBytes),
                jobs);

        Path work = Files.createTempDirectory("pdfbook-bench");
        try {
            Path out = work.resolve("out.pdf");
            List<String> convert = new ArrayList<>();
            convert.add(launcher.toString());
            convert.add(input.toString());
            convert.add("-o");
            convert.add(out.toString());
            convert.add("--force");
            convert.add("--timings");
            if (!"auto".equals(jobs)) {
                convert.add("-j");
                convert.add(jobs);
            }

            // Cold run (fresh page cache for the input is not guaranteed, but a fresh JVM is) —
            // recorded separately from the warm median.
            Timed cold = timed(convert);

            double[] walls = new double[runs];
            long[] rsss = new long[runs];
            Map<String, List<Double>> stages = new LinkedHashMap<>();
            for (int r = 0; r < runs; r++) {
                Timed t = timed(convert);
                walls[r] = t.elapsedSeconds();
                rsss[r] = t.maxRssKib();
                parseTimings(t.output())
                        .forEach(
                                (stage, seconds) ->
                                        stages.computeIfAbsent(stage, ignored -> new ArrayList<>())
                                                .add(seconds));
            }
            Map<String, Double> stageMedians = new LinkedHashMap<>();
            stages.forEach((stage, seconds) -> stageMedians.put(stage, median(seconds)));

            long outputBytes = Files.isRegularFile(out) ? Files.size(out) : -1;
            return new Row(
                    fileName(input),
                    jobs,
                    pages,
                    inputBytes,
                    median(walls),
                    cold.elapsedSeconds(),
                    stageMedians,
                    medianLong(rsss),
                    outputBytes);
        } finally {
            deleteTree(work);
        }
    }

    /**
     * The per-stage seconds of one run, keyed by stage label in print order ({@code total}
     * included). Repeated labels (a batch run) sum, though the harness always converts one book.
     */
    private static Map<String, Double> parseTimings(String output) {
        Map<String, Double> timings = new LinkedHashMap<>();
        Matcher m = TIMING.matcher(output);
        while (m.find()) {
            timings.merge(m.group(1), Double.parseDouble(m.group(2)), Double::sum);
        }
        return timings;
    }

    // Subprocess measurement

    /**
     * Runs {@code command}, returning its wall time, peak RSS (sampled from {@code
     * /proc/<pid>/status} {@code VmHWM}; -1 where unavailable), and its merged stdout+stderr (so
     * the {@code timing:} lines can be parsed). Output is drained on a separate thread so a chatty
     * child cannot deadlock on a full pipe.
     */
    private static Timed timed(List<String> command) throws IOException, InterruptedException {
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        var captured = new AtomicReference<>("");
        Thread drainer =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (var in = process.getInputStream()) {
                                        captured.set(
                                                new String(
                                                        in.readAllBytes(), StandardCharsets.UTF_8));
                                    } catch (IOException ignored) {
                                        // Process gone; whatever was read is lost — acceptable for
                                        // a benchmark.
                                    }
                                });

        Path status = Path.of("/proc", Long.toString(process.pid()), "status");
        long peakRssKib = -1;
        while (process.isAlive()) {
            if (System.nanoTime() - start > PROCESS_TIMEOUT_NANOS) {
                process.destroyForcibly();
                throw new IOException("timed command did not finish: " + command);
            }
            peakRssKib = Math.max(peakRssKib, readVmHwmKib(status));
            Thread.sleep(POLL_MILLIS);
        }
        double elapsed = (System.nanoTime() - start) / 1.0e9;
        int exit = process.waitFor();
        drainer.join();
        if (exit != 0) {
            throw new IOException(
                    "benchmark child failed with exit " + exit + ": " + captured.get());
        }
        return new Timed(elapsed, peakRssKib, captured.get());
    }

    /** Peak RSS (KiB) from {@code /proc/<pid>/status}, or -1 if unreadable / non-Linux. */
    private static long readVmHwmKib(Path status) {
        try {
            Matcher m = VM_HWM.matcher(Files.readString(status, StandardCharsets.UTF_8));
            return m.find() ? Long.parseLong(m.group(1)) : -1;
        } catch (IOException | RuntimeException e) {
            return -1; // process already exited, or /proc not present
        }
    }

    /** Page count via {@code qpdf --show-npages} (PATH or absolute), or -1 when unavailable. */
    private int pageCount(Path pdf) throws InterruptedException {
        try {
            Process process =
                    new ProcessBuilder(qpdf, "--show-npages", pdf.toString())
                            .redirectErrorStream(true)
                            .start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return -1;
            }
            // The count is the one digits-only line; qpdf may surround it with warning lines.
            return output.lines()
                    .map(String::strip)
                    .filter(line -> line.matches("\\d+"))
                    .findFirst()
                    .map(Integer::parseInt)
                    .orElse(-1);
        } catch (IOException e) {
            return -1; // qpdf not installed — page count is cosmetic here
        }
    }

    // Numeric helpers

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n == 0) {
            return 0;
        }
        return (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static double median(List<Double> values) {
        return median(values.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private static long medianLong(long[] values) {
        return Math.round(median(Arrays.stream(values).asDoubleStream().toArray()));
    }

    private static String mib(long bytes) {
        return bytes < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f", bytes / (double) MIB);
    }

    // Rendering

    private String render(List<Row> rows) {
        // Stage columns: the union of stage labels across rows in first-appearance order, with
        // "total" (the launcher's in-process conversion time) pulled out as its own column.
        Set<String> stageNames = new LinkedHashSet<>();
        for (Row row : rows) {
            stageNames.addAll(row.stageMedians().keySet());
        }
        stageNames.remove("total");

        var sb = new StringBuilder();
        sb.append("# pdfbook runtime baseline (stage-level)\n\n")
                .append("Generated by `PipelineBenchmark`")
                .append(" (`./gradlew :pipeline:app:benchPipeline`, in the dev container).\n")
                .append(
                        "Tracks the **per-stage wall-clock breakdown, end-to-end runtime and peak"
                                + " memory**\n")
                .append(
                        "of the installDist `pdfbook` launcher. Re-run after any change to the"
                                + " pipeline\n")
                .append(
                        "and compare against the previous run before merging (acceptance: ≥5%"
                                + " median\n")
                .append(
                        "total-wall improvement, or an explicit RSS/disk win, with output"
                                + " validated).\n\n");
        appendHostInfo(sb);
        sb.append("\n## Stage breakdown (warm median of ").append(runs).append(" runs)\n\n");
        sb.append(
                        "`conv` is the launcher's in-process total (`timing: total`);"
                                + " `startup+init` = E2E wall − conv\n")
                .append(
                        "(JVM boot + first-touch PDFBox/AWT init). `jobs=auto` is the launcher's"
                                + " CPU-count default.\n\n");
        sb.append("| Input | Jobs | Pages | E2E wall | conv |");
        for (String stage : stageNames) {
            sb.append(' ').append(stage).append(" |");
        }
        sb.append(" startup+init | Cold wall | Peak RSS (MiB) | Output (MiB) |\n");
        sb.append("|---|---|---:|---:|---:|");
        sb.append("---:|".repeat(stageNames.size()));
        sb.append("---:|---:|---:|---:|\n");
        for (Row row : rows) {
            double conv = row.stageMedians().getOrDefault("total", 0.0);
            sb.append("| ")
                    .append(row.name())
                    .append(" | ")
                    .append(row.jobs())
                    .append(" | ")
                    .append(pages(row.pages()))
                    .append(" | ")
                    .append(secs(row.wallMedian()))
                    .append(" | ")
                    .append(conv > 0 ? secs(conv) : "n/a")
                    .append(" |");
            for (String stage : stageNames) {
                Double seconds = row.stageMedians().get(stage);
                sb.append(' ').append(seconds == null ? "n/a" : secs(seconds)).append(" |");
            }
            sb.append(' ')
                    .append(conv > 0 ? secs(Math.max(0, row.wallMedian() - conv)) : "n/a")
                    .append(" | ")
                    .append(secs(row.coldWall()))
                    .append(" | ")
                    .append(rssMib(row.rssMedianKib()))
                    .append(" | ")
                    .append(mib(row.outputBytes()))
                    .append(" |\n");
        }
        sb.append("\n## Stage shares (of conv, warm median)\n\n")
                .append(
                        "The shares that decide where optimization effort goes: a stage that is"
                                + " ~5% of conv\n")
                .append("cannot pay for a parallelization rewrite no matter how elegant.\n\n");
        sb.append("| Input | Jobs |");
        for (String stage : stageNames) {
            sb.append(' ').append(stage).append(" |");
        }
        sb.append('\n').append("|---|---|").append("---:|".repeat(stageNames.size())).append('\n');
        for (Row row : rows) {
            double conv = row.stageMedians().getOrDefault("total", 0.0);
            sb.append("| ").append(row.name()).append(" | ").append(row.jobs()).append(" |");
            for (String stage : stageNames) {
                Double seconds = row.stageMedians().get(stage);
                sb.append(' ')
                        .append(
                                seconds == null || conv <= 0
                                        ? "n/a"
                                        : String.format(
                                                Locale.ROOT, "%.1f%%", seconds * 100.0 / conv))
                        .append(" |");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void appendHostInfo(StringBuilder sb) {
        String date =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
        long totalRamBytes = totalPhysicalMemoryBytes();
        sb.append("- Date (UTC): ").append(date).append('\n');
        sb.append("- Host: ")
                .append(System.getProperty("os.name", "?"))
                .append(' ')
                .append(System.getProperty("os.version", "?"))
                .append(' ')
                .append(System.getProperty("os.arch", "?"))
                .append(", ")
                .append(Runtime.getRuntime().availableProcessors())
                .append(" CPUs, RAM ")
                .append(totalRamBytes > 0 ? Math.round(totalRamBytes / 1.073741824e9) + "Gi" : "?")
                .append('\n');
        sb.append("- Launcher: `").append(launcher).append("`\n");
        sb.append("- Samples per measurement: cold (1st run) + warm median of ")
                .append(runs)
                .append(".\n");
        sb.append(
                        "- The default input is the deterministic synthetic fixture"
                                + " (`createSampleScan`,\n")
                .append(
                        "  seeded, so identical across machines). Real books are pluggable via"
                                + " `-Pinputs=\"…\"`;\n")
                .append("  only their page count and byte size are reported.\n");
    }

    private static long totalPhysicalMemoryBytes() {
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize();
        }
        return -1;
    }

    private static String secs(double seconds) {
        return String.format(Locale.ROOT, "%.2fs", seconds);
    }

    private static String pages(int pages) {
        return pages < 0 ? "?" : Integer.toString(pages);
    }

    private static String rssMib(long rssKib) {
        return rssKib < 0 ? "n/a" : Long.toString(Math.round(rssKib / 1024.0));
    }

    // Small utilities

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    private static void requireExecutable(Path path, String what, String hint) {
        if (!Files.isExecutable(path)) {
            System.err.println("error: " + what + " not found at " + path);
            System.err.println("       " + hint);
            System.exit(1);
        }
    }

    private static Path requireParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("output path has no parent: " + path);
        }
        return parent;
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
