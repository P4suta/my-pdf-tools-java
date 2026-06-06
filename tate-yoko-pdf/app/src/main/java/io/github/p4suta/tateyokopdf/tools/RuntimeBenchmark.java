package io.github.p4suta.tateyokopdf.tools;

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
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime performance + memory benchmark for the built jpackage launcher.
 *
 * <p>Splits a conversion's wall-clock into its parts without touching production code: end-to-end
 * wall (measured around the launcher process), the launcher's own in-process {@code "Done in"}, the
 * difference as JVM-startup+init, the bundled qpdf's linearisation time, a {@code --version}
 * startup floor, and peak RSS. Writes a Markdown report.
 *
 * <p>Everything is plain Java: wall time from {@link System#nanoTime()} and peak RSS by sampling
 * the child's {@code /proc/<pid>/status} {@code VmHWM} (the kernel's monotonic high-water mark).
 * Only the measured processes themselves remain subprocesses: the launcher and the bundled qpdf.
 * RSS sampling is Linux-only; elsewhere it reports {@code n/a} while timings still work.
 *
 * <p>Usage: {@code RuntimeBenchmark <launcher> <qpdf> <outDoc> <runs> <heapLabel> <input.pdf>...}
 */
public final class RuntimeBenchmark {

    private static final Pattern DONE_IN = Pattern.compile("Done in ([0-9.]+)");
    private static final Pattern VM_HWM = Pattern.compile("VmHWM:\\s*([0-9]+)");
    private static final long POLL_MILLIS = 5;
    private static final long PROCESS_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final long MIB = 1024L * 1024L;

    private final Path launcher;
    private final Path qpdf;
    private final Path outDoc;
    private final int runs;
    private final String heapLabel;

    private RuntimeBenchmark(Path launcher, Path qpdf, Path outDoc, int runs, String heapLabel) {
        this.launcher = launcher;
        this.qpdf = qpdf;
        this.outDoc = outDoc;
        this.runs = runs;
        this.heapLabel = heapLabel;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 6) {
            System.err.println(
                    "usage: RuntimeBenchmark <launcher> <qpdf> <outDoc> <runs> <heapLabel>"
                            + " <input.pdf>...");
            System.exit(2);
            return;
        }
        var benchmark =
                new RuntimeBenchmark(
                        Path.of(args[0]),
                        Path.of(args[1]),
                        Path.of(args[2]),
                        Integer.parseInt(args[3]),
                        args[4]);
        List<Path> inputs = Arrays.stream(args).skip(5).map(Path::of).toList();
        benchmark.run(inputs);
    }

    // Result records

    /**
     * One measured run: elapsed wall seconds, peak RSS (KiB, or -1 if unavailable), child output.
     */
    private record Timed(double elapsedSeconds, long maxRssKib, String output) {}

    /** A finished per-input measurement, ready to render. */
    private record Row(
            String name,
            int pages,
            long sizeBytes,
            double wallMedian,
            OptionalDouble doneMedian,
            double startup,
            OptionalDouble qpdfMedian,
            double coldWall,
            long rssMedianKib) {}

    // Orchestration

    private void run(List<Path> inputs) throws IOException, InterruptedException {
        requireExecutable(launcher, "app-image launcher", "build it first:  just package");

        System.err.println("Measuring JVM startup floor (launcher --version)…");
        var floor = measureFloor();

        List<Row> rows = new ArrayList<>();
        for (Path input : inputs) {
            if (!Files.isRegularFile(input)) {
                System.err.println("skip (not found): " + input);
                continue;
            }
            rows.add(measureInput(input));
        }

        String report = render(floor, rows);
        Files.createDirectories(requireParent(outDoc));
        Files.writeString(outDoc, report, StandardCharsets.UTF_8);
        System.out.print(report);
        System.err.println();
        System.err.println("→ wrote " + outDoc);
    }

    private record Floor(double warmMedian, double cold) {}

    private Floor measureFloor() throws IOException, InterruptedException {
        double cold = 0;
        double[] warm = new double[runs];
        for (int r = 0; r <= runs; r++) {
            Timed t = timed(List.of(launcher.toString(), "--version"));
            if (r == 0) {
                cold = t.elapsedSeconds();
            } else {
                warm[r - 1] = t.elapsedSeconds();
            }
        }
        return new Floor(median(warm), cold);
    }

    private Row measureInput(Path input) throws IOException, InterruptedException {
        int pages = pageCount(input);
        long sizeBytes = Files.size(input);
        System.err.printf(
                Locale.ROOT,
                "Measuring: %s (%dp, %s MiB)…%n",
                fileName(input),
                pages,
                mib(sizeBytes));

        Path work = Files.createTempDirectory("tate-yoko-bench");
        try {
            Path out = work.resolve("out.pdf");
            List<String> convert =
                    List.of(launcher.toString(), input.toString(), "-o", out.toString());

            // Cold run (first invocation in a fresh JVM) — recorded separately.
            Timed cold = timed(convert);

            double[] walls = new double[runs];
            long[] rsss = new long[runs];
            List<Double> dones = new ArrayList<>();
            for (int r = 0; r < runs; r++) {
                Timed t = timed(convert);
                walls[r] = t.elapsedSeconds();
                rsss[r] = t.maxRssKib();
                parseDoneSeconds(t.output()).ifPresent(dones::add);
            }
            double wallMedian = median(walls);
            OptionalDouble doneMedian = median(dones);
            double startup = Math.max(0, wallMedian - doneMedian.orElse(0));

            OptionalDouble qpdfMedian = measureQpdf(out, work);

            return new Row(
                    fileName(input),
                    pages,
                    sizeBytes,
                    wallMedian,
                    doneMedian,
                    startup,
                    qpdfMedian,
                    cold.elapsedSeconds(),
                    medianLong(rsss));
        } finally {
            deleteTree(work);
        }
    }

    /** Times the bundled qpdf linearizing the converted output, to size the post-process pass. */
    private OptionalDouble measureQpdf(Path converted, Path work)
            throws IOException, InterruptedException {
        if (!Files.isRegularFile(converted) || !Files.isExecutable(qpdf)) {
            return OptionalDouble.empty();
        }
        double[] qs = new double[runs];
        for (int r = 0; r < runs; r++) {
            Timed t =
                    timed(
                            List.of(
                                    qpdf.toString(),
                                    "--linearize",
                                    converted.toString(),
                                    work.resolve("q.pdf").toString()));
            qs[r] = t.elapsedSeconds();
        }
        return OptionalDouble.of(median(qs));
    }

    // Subprocess measurement

    /**
     * Runs {@code command}, returning its wall time ({@link System#nanoTime()} around the process),
     * peak RSS (sampled from {@code /proc/<pid>/status} {@code VmHWM}; -1 where unavailable), and
     * its merged stdout+stderr (so {@code "Done in"} can be parsed). Output is drained on a
     * separate thread so a chatty child cannot deadlock on a full pipe.
     */
    private Timed timed(List<String> command) throws IOException, InterruptedException {
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
        process.waitFor();
        drainer.join();
        return new Timed(elapsed, peakRssKib, captured.get());
    }

    /**
     * Peak RSS (KiB) from {@code /proc/<pid>/status} {@code VmHWM}, or -1 if unreadable /
     * non-Linux.
     */
    private static long readVmHwmKib(Path status) {
        try {
            Matcher m = VM_HWM.matcher(Files.readString(status, StandardCharsets.UTF_8));
            return m.find() ? Long.parseLong(m.group(1)) : -1;
        } catch (IOException | RuntimeException e) {
            return -1; // process already exited, or /proc not present
        }
    }

    /** Page count via {@code qpdf --show-npages} (exit 3 = warning is fine; keep the digits). */
    private int pageCount(Path pdf) throws IOException, InterruptedException {
        if (!Files.isExecutable(qpdf)) {
            return -1;
        }
        Process process =
                new ProcessBuilder(qpdf.toString(), "--show-npages", pdf.toString())
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
        String digits = output.replaceAll("\\D", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }

    // Numeric helpers

    private static OptionalDouble parseDoneSeconds(String output) {
        Matcher m = DONE_IN.matcher(output);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(last));
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n == 0) {
            return 0;
        }
        return (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static OptionalDouble median(List<Double> values) {
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(
                median(values.stream().mapToDouble(Double::doubleValue).toArray()));
    }

    private static long medianLong(long[] values) {
        return Math.round(median(Arrays.stream(values).asDoubleStream().toArray()));
    }

    private static String mib(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (double) MIB);
    }

    // Rendering

    private String render(Floor floor, List<Row> rows) {
        var sb = new StringBuilder();
        sb.append("# Runtime performance & memory baseline\n\n")
                .append("Generated by `RuntimeBenchmark` (`just bench-runtime`). Tracks\n")
                .append("**conversion runtime and peak memory**, separate from the CI/build-time\n")
                .append(
                        "numbers in `perf-baseline.md`. Re-run after any change to the"
                                + " conversion\n")
                .append("pipeline, stream-cache policy, or launcher heap options.\n\n");
        appendHostInfo(sb);
        sb.append("\n## Time breakdown (warm median of ")
                .append(runs)
                .append(" runs)\n\n")
                .append(
                        "Each invocation is a **fresh JVM process** (the realistic single-file"
                                + " usage:\n")
                .append(
                        "the user runs the command once per file). Batch mode amortizes this"
                                + " startup\n")
                .append("across files in one process, so per-file cost there is much lower.\n\n")
                .append("JVM startup floor (`--version`, no PDFBox/AWT init): **")
                .append(secs(floor.warmMedian()))
                .append("** warm / ")
                .append(secs(floor.cold()))
                .append(" cold.\n")
                .append(
                        "`startup` below = end-to-end wall − in-process `Done in`; it includes"
                                + " JVM\n")
                .append(
                        "boot **and** first-touch PDFBox/AWT/font init, so it exceeds the bare"
                                + " floor.\n\n")
                .append(
                        "| Input | Pages | Size (MiB) | E2E wall | Done (conv) | Startup＋init |"
                                + " qpdf alone | Cold wall |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Row row : rows) {
            sb.append("| ")
                    .append(row.name())
                    .append(" | ")
                    .append(pages(row.pages()))
                    .append(" | ")
                    .append(mib(row.sizeBytes()))
                    .append(" | ")
                    .append(secs(row.wallMedian()))
                    .append(" | ")
                    .append(optSecs(row.doneMedian()))
                    .append(" | ")
                    .append(secs(row.startup()))
                    .append(" | ")
                    .append(optSecs(row.qpdfMedian()))
                    .append(" | ")
                    .append(secs(row.coldWall()))
                    .append(" |\n");
        }
        sb.append("\n## Peak memory vs input size (warm median)\n\n")
                .append(
                        "Peak RSS = a fixed **~115 MiB JVM/PDFBox/AWT floor** + the document held"
                                + " in\n")
                .append(
                        "heap. The floor dominates small inputs (so `RSS/size` looks huge and"
                                + " then\n")
                .append(
                        "falls); for large inputs the marginal RSS settles to **roughly the"
                                + " input\n")
                .append(
                        "size** — that is the output document's memory-only stream cache"
                                + " accumulating\n")
                .append(
                        "every cloned page stream until save. Extrapolating that slope against"
                                + " the\n")
                .append(
                        "launcher heap locates the input size at which it would OOM: the cliff"
                                + " this\n")
                .append(
                        "benchmark exists to find. (Peak RSS is sampled from `/proc`, so a very"
                                + " short\n")
                .append("run can read slightly under the true peak.)\n\n")
                .append("| Input | Pages | Size (MiB) | Peak RSS (MiB) | RSS/size |\n")
                .append("|---|---:|---:|---:|---:|\n");
        for (Row row : rows) {
            sb.append("| ")
                    .append(row.name())
                    .append(" | ")
                    .append(pages(row.pages()))
                    .append(" | ")
                    .append(mib(row.sizeBytes()))
                    .append(" | ")
                    .append(rssMib(row.rssMedianKib()))
                    .append(" | ")
                    .append(rssRatio(row.rssMedianKib(), row.sizeBytes()))
                    .append(" |\n");
        }
        sb.append("\n## Memory safety\n\n")
                .append("Two defenses keep the heap from being exhausted by a very large scan:\n\n")
                .append("1. **RAM-proportional heap** — the launcher runs with\n")
                .append(
                        "   `-XX:MaxRAMPercentage=75.0` (see `app/build.gradle.kts`), so the"
                                + " heap\n")
                .append(
                        "   scales to the host and honors container cgroup limits instead of a"
                                + " fixed 2g.\n")
                .append("2. **`--low-memory`** — spills cloned page streams to a temp file\n")
                .append("   (`java.io.tmpdir`) via PDFBox's scratch stream cache, bounding heap\n")
                .append("   regardless of input size, at the cost of some disk I/O.\n\n")
                .append("Verified on a large input under a deliberately tiny heap: with\n")
                .append(
                        "`JAVA_TOOL_OPTIONS=-Xmx64m` the default (in-memory) conversion exits"
                                + " 137\n")
                .append("(OutOfMemory), while the same conversion with `--low-memory` completes\n")
                .append("successfully and produces an identical page count. Note that if\n")
                .append("`java.io.tmpdir` is a tmpfs (RAM-backed), `--low-memory` does not free\n")
                .append("physical memory — point it at real disk on memory-constrained hosts.\n");
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
        sb.append("- Launcher: `")
                .append(launcher)
                .append("` (heap: ")
                .append(heapLabel)
                .append(")\n");
        sb.append("- Samples per measurement: cold (1st run) + warm median of ")
                .append(runs)
                .append(".\n");
        sb.append(
                        "- Inputs beyond `sample.pdf` are a private local corpus (not distributed);"
                                + " only\n")
                .append(
                        "  page count and byte size are reported. Pass your own files to `just"
                                + " bench-runtime`.\n");
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

    private static String optSecs(OptionalDouble seconds) {
        return seconds.isPresent() ? secs(seconds.getAsDouble()) : "n/a";
    }

    private static String pages(int pages) {
        return pages < 0 ? "?" : Integer.toString(pages);
    }

    private static String rssMib(long rssKib) {
        return rssKib < 0 ? "n/a" : Long.toString(Math.round(rssKib / 1024.0));
    }

    /** RSS/size ratio, meaningful only once the document dwarfs the ~115 MiB floor (≥ 1 MiB). */
    private static String rssRatio(long rssKib, long sizeBytes) {
        if (rssKib < 0 || sizeBytes < MIB) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f×", (rssKib * 1024.0) / sizeBytes);
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
