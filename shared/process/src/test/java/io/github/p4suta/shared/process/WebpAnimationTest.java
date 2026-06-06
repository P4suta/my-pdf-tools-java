package io.github.p4suta.shared.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebpAnimationTest {

    @Test
    void sampleFramesKeepsAllWhenUnderCap() {
        List<Path> frames = paths(5);
        assertEquals(frames, WebpAnimation.sampleFrames(frames, 300));
    }

    @Test
    void sampleFramesEvenlyDownsamplesPastCap() {
        List<Path> frames = paths(10);
        // 10 frames, cap 3 -> stride ceil(10/3)=4 -> indices 0,4,8
        List<Path> sampled = WebpAnimation.sampleFrames(frames, 3);
        assertEquals(List.of(frames.get(0), frames.get(4), frames.get(8)), sampled);
    }

    @Test
    void sampleFramesRejectsNonPositiveCap() {
        assertThrows(IllegalArgumentException.class, () -> WebpAnimation.sampleFrames(paths(2), 0));
    }

    @Test
    void buildCommandAssemblesLosslessLoopArgv() {
        List<String> cmd =
                WebpAnimation.buildCommand(
                        Path.of("/usr/bin/img2webp"),
                        List.of(Path.of("a.png"), Path.of("b.png")),
                        Path.of("out.webp"),
                        150);
        assertEquals(
                List.of(
                        "/usr/bin/img2webp",
                        "-loop",
                        "0",
                        "-lossless",
                        "-d",
                        "150",
                        "a.png",
                        "b.png",
                        "-o",
                        "out.webp"),
                cmd);
    }

    @Test
    void assembleReturnsFalseForNoFrames(@TempDir Path dir) throws Exception {
        assertFalse(
                WebpAnimation.assemble(
                        List.of(), dir.resolve("x.webp"), 150, 300, "p4suta.img2webp.path"));
    }

    @Test
    void assembleReturnsFalseWhenToolCannotStart(@TempDir Path dir) throws Exception {
        Path frame = dir.resolve("f.png");
        writePng(frame);
        // An override pointing at a non-existent binary resolves but fails to launch, so assemble
        // returns false.
        String key = "p4suta.test.img2webp.path";
        System.setProperty(key, dir.resolve("does-not-exist").toString());
        try {
            assertFalse(
                    WebpAnimation.assemble(List.of(frame), dir.resolve("x.webp"), 150, 300, key));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void assembleWritesAnimatedWebpWithRealTool(@TempDir Path dir) throws Exception {
        assumeTrue(
                ToolPath.resolve("img2webp", "p4suta.absent").isPresent(),
                "img2webp must be installed");
        List<Path> frames = List.of(dir.resolve("0.png"), dir.resolve("1.png"));
        for (Path f : frames) {
            writePng(f);
        }
        Path out = dir.resolve("anim.webp");
        assertTrue(
                WebpAnimation.assemble(frames, out, 100, 300, "p4suta.absent"),
                "img2webp should assemble the loop");
        assertTrue(Files.size(out) > 0);
    }

    private static void writePng(Path path) throws Exception {
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", path.toFile());
    }

    private static List<Path> paths(int n) {
        return IntStream.range(0, n).mapToObj(i -> Path.of(i + ".png")).toList();
    }
}
