package io.github.p4suta.shared.process;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles an animated, looping, lossless WebP from a sequence of frames via libwebp's {@code
 * img2webp} — the single cross-app home for the "flip-book" both register (registered pages) and
 * despeckle (removed-pixel overlays) build. Neither the JDK nor Leptonica writes animated WebP, so
 * this shells out; but unlike the per-app copies it replaced, it runs through the shared {@link
 * ToolPath} + {@link ProcessRunner} (so the timeout/kill and exit handling are the already-tested
 * ones, not hand-rolled).
 *
 * <p>If {@code img2webp} is missing or fails the flip-book is skipped (returns {@code false}) and
 * the caller's other artifacts are unaffected. The binary is resolved from the caller-supplied
 * {@code -D<propertyKey>} override (e.g. {@code register.img2webp.path}) else {@code img2webp} on
 * {@code PATH} — the key stays a per-app parameter so packaged app-images keep resolving.
 */
public final class WebpAnimation {

    private static final Logger LOG = LoggerFactory.getLogger(WebpAnimation.class);

    /**
     * Default frame cap so a long book stays a small artifact; frames are sampled evenly past it.
     */
    public static final int DEFAULT_MAX_FRAMES = 300;

    /** Default per-frame display duration in milliseconds. */
    public static final int DEFAULT_DELAY_MS = 150;

    private static final long TIMEOUT_SECONDS = 300;

    private WebpAnimation() {}

    /**
     * Evenly sample {@code frames} down to at most {@code maxFrames} (stride = {@code ceil(n /
     * maxFrames)}), preserving order. Returns the input as-is when already within the cap.
     *
     * @param frames the frames in display order
     * @param maxFrames the cap (must be positive)
     * @return the sampled frames, in order
     */
    public static List<Path> sampleFrames(List<Path> frames, int maxFrames) {
        if (maxFrames < 1) {
            throw new IllegalArgumentException("maxFrames must be positive: " + maxFrames);
        }
        int stride = (frames.size() + maxFrames - 1) / maxFrames;
        if (stride <= 1) {
            return List.copyOf(frames);
        }
        List<Path> sampled = new ArrayList<>();
        for (int i = 0; i < frames.size(); i += stride) {
            sampled.add(frames.get(i));
        }
        return sampled;
    }

    /** Build the {@code img2webp} argument vector (package-private for unit testing). */
    static List<String> buildCommand(Path bin, List<Path> frames, Path out, int delayMs) {
        List<String> cmd = new ArrayList<>();
        cmd.add(bin.toString());
        cmd.add("-loop");
        cmd.add("0");
        cmd.add("-lossless");
        cmd.add("-d");
        cmd.add(String.valueOf(delayMs));
        for (Path frame : frames) {
            cmd.add(frame.toString());
        }
        cmd.add("-o");
        cmd.add(out.toString());
        return cmd;
    }

    /**
     * Assemble {@code frames} into {@code out} as an animated lossless WebP loop, sampling down to
     * {@code maxFrames}.
     *
     * @param frames the frames in display order (image files img2webp can read; PNG or WebP)
     * @param out the animated WebP to write
     * @param delayMs per-frame display duration
     * @param maxFrames the frame cap
     * @param toolPropertyKey the {@code -D} override key for the img2webp path (per-app)
     * @return {@code true} if the flip-book was written; {@code false} if img2webp is unavailable
     *     or failed (the caller keeps its other artifacts)
     * @throws IOException if the assembly is interrupted
     */
    public static boolean assemble(
            List<Path> frames, Path out, int delayMs, int maxFrames, String toolPropertyKey)
            throws IOException {
        if (frames.isEmpty()) {
            return false;
        }
        Optional<Path> bin = ToolPath.resolve("img2webp", toolPropertyKey);
        if (bin.isEmpty()) {
            LOG.warn(
                    "img2webp not found; skipping the WebP flip-book — install libwebp's img2webp"
                            + " or set -D{}",
                    toolPropertyKey);
            return false;
        }
        List<Path> sampled = sampleFrames(frames, maxFrames);
        try {
            ProcessRunner.run(
                    buildCommand(bin.get(), sampled, out, delayMs),
                    Duration.ofSeconds(TIMEOUT_SECONDS));
            LOG.info("animated WebP written to {} ({} frames)", out, sampled.size());
            return true;
        } catch (IOException | TimeoutException e) {
            LOG.warn("img2webp failed ({}); WebP flip-book not written", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("WebP flip-book assembly interrupted", e);
        }
    }
}
