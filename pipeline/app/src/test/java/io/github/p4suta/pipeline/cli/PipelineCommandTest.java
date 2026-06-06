package io.github.p4suta.pipeline.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the {@code pdfbook} front end's parse layer and exit-code contract: {@code 0} for
 * help/version/no-args, {@code 2} for usage/parse/type errors. Every case short-circuits before the
 * pipeline runs (no native tools), mirroring {@code DespeckleCliTest}.
 */
final class PipelineCommandTest {

    private static int run(String... args) {
        return new PipelineCommand().run(args);
    }

    // ---- exit 0: help / version / no-args short-circuit ----

    @Test
    void noArgumentsPrintsHelpAndExitsZero() {
        assertEquals(0, run());
    }

    @Test
    void helpExitsZero() {
        assertEquals(0, run("--help"));
        assertEquals(0, run("-h"));
    }

    @Test
    void versionExitsZero() {
        assertEquals(0, run("--version"));
        assertEquals(0, run("-V"));
    }

    // ---- exit 2: usage / parse / type errors ----

    @Test
    void unknownOptionIsUsageError() {
        assertEquals(2, run("--bogus"));
    }

    @Test
    void missingOutputIsUsageError() {
        assertEquals(2, run("in.pdf"));
    }

    @Test
    void noInputIsUsageError() {
        assertEquals(2, run("-o", "out.pdf"));
    }

    @Test
    void stdinInputIsRejected() {
        assertEquals(2, run("-", "-o", "out.pdf"));
    }

    @Test
    void invalidDirectionIsUsageError() {
        assertEquals(2, run("in.pdf", "-o", "out.pdf", "--direction", "sideways"));
    }

    @Test
    void invalidFirstPageIsUsageError() {
        assertEquals(2, run("in.pdf", "-o", "out.pdf", "--first-page", "middle"));
    }

    @Test
    void nonNumericJobsIsUsageError() {
        assertEquals(2, run("in.pdf", "-o", "out.pdf", "--jobs", "many"));
    }
}
