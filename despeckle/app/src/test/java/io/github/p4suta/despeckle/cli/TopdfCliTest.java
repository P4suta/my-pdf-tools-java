package io.github.p4suta.despeckle.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the {@code despeckle topdf} sub-command's parse layer: routing through {@link DespeckleCli},
 * the exact-2-positionals contract, and the exit-code mapping (0 success / 2 usage / the sysexits
 * codes for runtime failures — 64 INVALID_PARAMETER, 66 INPUT_NOT_FOUND). Tool-free — every case
 * short-circuits before {@code jbig2} runs.
 */
final class TopdfCliTest {

    private static int run(String... args) {
        return new DespeckleCli().run(args);
    }

    @Test
    void noArgsPrintsHelpAndExitsZero() {
        assertEquals(0, run("topdf"));
    }

    @Test
    void onePositionalIsUsageError() {
        assertEquals(2, run("topdf", "cleaned"));
    }

    @Test
    void threePositionalsIsUsageError() {
        assertEquals(2, run("topdf", "a", "b", "c"));
    }

    @Test
    void unknownOptionIsUsageError() {
        assertEquals(2, run("topdf", "a", "b", "--bogus"));
    }

    @Test
    void nonNumericDpiIsUsageError() {
        assertEquals(2, run("topdf", "a", "b", "--dpi", "x"));
    }

    @Test
    void helpExitsZero() {
        assertEquals(0, run("topdf", "--help"));
        assertEquals(0, run("topdf", "-h"));
    }

    @Test
    void nonPositiveDpiIsInvalidParameter(@TempDir Path tmp) {
        // --dpi 0 is a bad CLI value (IllegalArgumentException) -> INVALID_PARAMETER -> EX_USAGE
        // (64) per the error-model spec.
        assertEquals(
                64,
                run(
                        "topdf",
                        tmp.resolve("dir").toString(),
                        tmp.resolve("out.pdf").toString(),
                        "--dpi",
                        "0",
                        "--force"));
    }

    @Test
    void missingImageDirIsInputNotFound(@TempDir Path tmp) {
        // Jbig2PackService rejects a non-existent image directory before shelling out to any tool;
        // it now throws DespeckleException(INPUT_NOT_FOUND) -> EX_NOINPUT (66) per the error-model
        // spec.
        assertEquals(
                66,
                run(
                        "topdf",
                        tmp.resolve("nodir").toString(),
                        tmp.resolve("out.pdf").toString(),
                        "--force"));
    }
}
