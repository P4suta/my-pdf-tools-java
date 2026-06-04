package io.github.p4suta.shared.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolPathTest {

    @Test
    void overridePropertyWins() {
        String propertyKey = "shared.test.toolpath.override";
        System.setProperty(propertyKey, "/opt/custom/tool");
        try {
            // The override is taken verbatim as a Path; the tool name is never consulted.
            assertEquals(
                    Optional.of(Path.of("/opt/custom/tool")),
                    ToolPath.resolve("whatever", propertyKey));
        } finally {
            System.clearProperty(propertyKey);
        }
    }

    @Test
    void emptyWhenNoOverrideAndNotOnPath() {
        // An unset override property plus a name that cannot be on PATH yields no resolution.
        assertTrue(
                ToolPath.resolve("shared-no-such-tool-xyz", "shared.test.toolpath.unset")
                        .isEmpty());
    }

    @Test
    void blankOverrideFallsThroughToPath() {
        String propertyKey = "shared.test.toolpath.blank";
        System.setProperty(propertyKey, "   ");
        try {
            // A blank override is ignored and the PATH search runs, finding nothing for this name.
            assertTrue(ToolPath.resolve("shared-no-such-tool-xyz", propertyKey).isEmpty());
        } finally {
            System.clearProperty(propertyKey);
        }
    }

    @Test
    void findsAnExecutableOnPath() {
        // /bin/sh is on PATH in the build container, so the PATH branch resolves it to a real,
        // executable path.
        Optional<Path> resolved = ToolPath.resolve("sh", "shared.test.toolpath.findsh");
        assertTrue(resolved.isPresent());
        Path shPath = resolved.orElseThrow();
        assertTrue(shPath.endsWith("sh"));
        assertTrue(shPath.isAbsolute());
    }
}
