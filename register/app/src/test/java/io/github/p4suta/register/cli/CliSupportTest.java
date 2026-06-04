package io.github.p4suta.register.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.p4suta.register.domain.model.PaperSize;
import org.junit.jupiter.api.Test;

/**
 * The register-specific CLI parsing logic. The app-neutral int/double/enum/positional parsers moved
 * to the shared {@code CliOptionSupport} (covered by its own {@code CliOptionSupportTest}); only
 * the {@code auto}/paper-name resolution that stays in {@link CliSupport} is exercised here.
 */
class CliSupportTest {

    @Test
    void parsePaperTreatsNullAndAutoAsAuto() {
        assertNull(CliSupport.parsePaper(null));
        assertNull(CliSupport.parsePaper("auto"));
    }

    @Test
    void parsePaperResolvesAStandardName() {
        assertEquals(PaperSize.Standard.A6, CliSupport.parsePaper("a6"));
    }
}
