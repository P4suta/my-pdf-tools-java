package io.github.p4suta.register.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.domain.model.Anchor;
import java.nio.file.Path;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

/** The {@code register pipeline} front end's argument parsing (the shared seam, no I/O). */
class PipelineCommandTest {

    private static PipelineCommand.Parsed parse(String... args) throws ParseException {
        return new PipelineCommand().parse(args);
    }

    @Test
    void positionalsBecomeInputAndOutput() throws ParseException {
        PipelineCommand.Parsed parsed = parse("in.pdf", "out.pdf");
        assertEquals(Path.of("in.pdf"), parsed.input());
        assertEquals(Path.of("out.pdf"), parsed.output());
    }

    @Test
    void suffixDefaultsToEmpty() throws ParseException {
        assertEquals("", parse("in", "out").suffix());
    }

    @Test
    void suffixIsParsed() throws ParseException {
        assertEquals("_registered", parse("in", "out", "--suffix", "_registered").suffix());
    }

    @Test
    void forceDefaultsOffAndParses() throws ParseException {
        assertFalse(parse("in", "out").force());
        assertTrue(parse("in", "out", "--force").force());
    }

    @Test
    void sharesRegistrationDefaultsWithRegister() throws ParseException {
        var options = parse("in", "out").options();
        assertTrue(options.deskew());
        assertTrue(options.scale());
        assertEquals(Anchor.TOP_RIGHT, options.anchor());
    }

    @Test
    void offSwitchesTurnDeskewAndScaleOff() throws ParseException {
        var options = parse("in", "out", "--no-deskew", "--no-scale").options();
        assertFalse(options.deskew());
        assertFalse(options.scale());
    }

    @Test
    void missingPositionalIsRejected() {
        assertThrows(ParseException.class, () -> parse("only-one"));
    }
}
