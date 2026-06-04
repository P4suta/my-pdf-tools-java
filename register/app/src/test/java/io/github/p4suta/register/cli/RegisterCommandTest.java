package io.github.p4suta.register.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.application.RegistrationService;
import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.OutputFormat;
import io.github.p4suta.register.domain.model.PaperSize;
import io.github.p4suta.register.domain.model.RegisterOptions;
import java.nio.file.Path;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

/** The Commons CLI front end's argument parsing — defaults, off switches, enums, positionals. */
class RegisterCommandTest {

    private static RegistrationService.Config parse(String... args) throws ParseException {
        return new RegisterCommand().parse(args);
    }

    @Test
    void deskewScaleAndAnchorDefaults() throws ParseException {
        RegisterOptions options = parse("in", "out").options();
        assertTrue(options.deskew());
        assertTrue(options.scale());
        assertEquals(Anchor.TOP_RIGHT, options.anchor());
    }

    @Test
    void offSwitchesTurnDeskewAndScaleOff() throws ParseException {
        RegisterOptions options = parse("in", "out", "--no-deskew", "--no-scale").options();
        assertFalse(options.deskew(), "--no-deskew must turn deskew off");
        assertFalse(options.scale(), "--no-scale must turn scale off");
    }

    @Test
    void paperDefaultsToAuto() throws ParseException {
        assertNull(parse("in", "out").options().paper(), "no --paper means auto-detect");
    }

    @Test
    void paperAutoIsTreatedAsAuto() throws ParseException {
        assertNull(parse("in", "out", "--paper", "auto").options().paper());
    }

    @Test
    void explicitPaperOverridesAuto() throws ParseException {
        assertEquals(PaperSize.Standard.A6, parse("in", "out", "--paper", "a6").options().paper());
    }

    @Test
    void enumValuesAreCaseInsensitive() throws ParseException {
        RegistrationService.Config config =
                parse("in", "out", "--format", "png", "--anchor", "top_right");
        assertEquals(OutputFormat.PNG, config.format());
        assertEquals(Anchor.TOP_RIGHT, config.options().anchor());
    }

    @Test
    void positionalsBecomeInputAndOutputDirs() throws ParseException {
        RegistrationService.Config config = parse("in", "out");
        assertEquals(Path.of("in"), config.inputDir());
        assertEquals(Path.of("out"), config.outputDir());
    }

    @Test
    void flipbookDefaultsOff() throws ParseException {
        assertFalse(parse("in", "out").flipbook());
    }

    @Test
    void flipbookWithDiagParses() throws ParseException {
        RegistrationService.Config config = parse("in", "out", "--diag", "d", "--flipbook");
        assertTrue(config.flipbook());
        assertEquals(Path.of("d"), config.diagDir());
    }

    @Test
    void flipbookWithoutDiagIsRejected() {
        assertThrows(ParseException.class, () -> parse("in", "out", "--flipbook"));
    }

    @Test
    void invalidEnumValueIsRejected() {
        assertThrows(ParseException.class, () -> parse("in", "out", "--format", "jpeg"));
    }

    @Test
    void missingPositionalIsRejected() {
        assertThrows(ParseException.class, () -> parse("only-one"));
    }
}
