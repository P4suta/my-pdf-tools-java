package io.github.p4suta.register.infrastructure.diag;

import io.github.p4suta.register.infrastructure.process.TempDirs;
import io.github.p4suta.shared.imaging.Pix;
import io.github.p4suta.shared.process.ToolPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the registered output pages into an animated WebP "flip-book", so the steadiness of the
 * text block across pages is visible at a glance — the most direct demonstration that registration
 * worked. WebP keeps the artifact small, but neither the JDK nor Leptonica can write animated WebP,
 * so this shells out to libwebp's {@code img2webp}. If that tool is not installed the flip-book is
 * skipped with a warning and the rest of the diagnostics are unaffected.
 *
 * <p>The {@code img2webp} binary is taken from {@code -Dregister.img2webp.path} when set, otherwise
 * {@code img2webp} on the {@code PATH}.
 */
final class WebpFlipbook {

    private static final Logger LOG = LoggerFactory.getLogger(WebpFlipbook.class);

    /** Frames taller than this are downscaled; the flip-book is a preview, not an archival copy. */
    private static final int MAX_FRAME_HEIGHT = 1000;

    /**
     * Cap the frame count so a long book stays a small artifact; pages are sampled evenly past it.
     */
    private static final int MAX_FRAMES = 300;

    /** Per-frame display duration in milliseconds. */
    private static final int DELAY_MS = 150;

    private static final long TIMEOUT_SECONDS = 300;

    private WebpFlipbook() {}

    /**
     * Build {@code dir/flipbook.webp} from {@code outputs} (the registered pages, in reading
     * order).
     *
     * @param dir the diagnostics directory
     * @param outputs the registered output image paths, in reading order
     * @throws IOException if frame extraction or filesystem work fails
     */
    static void write(Path dir, List<Path> outputs) throws IOException {
        if (outputs.isEmpty()) {
            return;
        }
        int stride = (outputs.size() + MAX_FRAMES - 1) / MAX_FRAMES;
        if (stride > 1) {
            LOG.info(
                    "flip-book: {} pages exceeds the {}-frame cap; sampling every {} page(s)",
                    outputs.size(),
                    MAX_FRAMES,
                    stride);
        }
        Path framesDir = Files.createTempDirectory(dir, ".flipbook-frames-");
        try {
            List<Path> frames = extractFrames(outputs, stride, framesDir);
            if (!frames.isEmpty()) {
                runImg2webp(frames, dir.resolve("flipbook.webp"));
            }
        } finally {
            TempDirs.deleteRecursively(framesDir);
        }
    }

    private static List<Path> extractFrames(List<Path> outputs, int stride, Path framesDir)
            throws IOException {
        List<Path> frames = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < outputs.size(); i += stride) {
            Path frame = framesDir.resolve(String.format(Locale.ROOT, "frame%05d.png", n++));
            try (Pix page = Pix.read(outputs.get(i))) {
                if (page.height() > MAX_FRAME_HEIGHT) {
                    try (Pix small = page.scaleToHeight(MAX_FRAME_HEIGHT)) {
                        small.writePng(frame);
                    }
                } else {
                    page.writePng(frame);
                }
            }
            frames.add(frame);
        }
        return frames;
    }

    private static void runImg2webp(List<Path> frames, Path webp) throws IOException {
        Optional<Path> resolved = ToolPath.resolve("img2webp", "register.img2webp.path");
        if (resolved.isEmpty()) {
            LOG.warn(
                    "img2webp not found on PATH; skipping the WebP flip-book — install libwebp's"
                            + " img2webp or set -Dregister.img2webp.path");
            return;
        }
        String bin = resolved.get().toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(bin);
        cmd.add("-loop");
        cmd.add("0");
        cmd.add("-lossless");
        cmd.add("-d");
        cmd.add(String.valueOf(DELAY_MS));
        for (Path frame : frames) {
            cmd.add(frame.toString());
        }
        cmd.add("-o");
        cmd.add(webp.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            LOG.warn("could not run '{}' ({}); skipping the WebP flip-book", bin, e.getMessage());
            return;
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("img2webp timed out after {}s; flip-book not written", TIMEOUT_SECONDS);
                return;
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("flip-book assembly interrupted", e);
        }
        int code = process.exitValue();
        if (code != 0) {
            LOG.warn("img2webp exited with status {}; flip-book not written", code);
            return;
        }
        LOG.info("flip-book written to {} ({} frames)", webp, frames.size());
    }
}
