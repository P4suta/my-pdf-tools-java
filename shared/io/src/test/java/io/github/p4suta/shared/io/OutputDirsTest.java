package io.github.p4suta.shared.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputDirsTest {

    @Test
    void createsTheDirectoryWhenAbsent(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("nested").resolve("out");
        assertThat(Files.exists(out)).isFalse();

        OutputDirs.prepare(out, false);

        assertThat(Files.isDirectory(out)).isTrue();
    }

    @Test
    void emptyExistingDirectoryIsAccepted(@TempDir Path tmp) throws IOException {
        Path out = Files.createDirectory(tmp.resolve("out"));

        // An existing-but-empty directory is fine even without force, and is left in place.
        assertThatCode(() -> OutputDirs.prepare(out, false)).doesNotThrowAnyException();
        assertThat(Files.isDirectory(out)).isTrue();
    }

    @Test
    void nonEmptyDirectoryWithoutForceThrowsTheExactMessage(@TempDir Path tmp) throws IOException {
        Path out = Files.createDirectory(tmp.resolve("out"));
        Files.createFile(out.resolve("already-here.txt"));

        // The message is part of the apps' observable contract — asserted verbatim.
        assertThatThrownBy(() -> OutputDirs.prepare(out, false))
                .isInstanceOf(IOException.class)
                .hasMessage("output directory " + out + " is not empty; pass --force to overwrite");
    }

    @Test
    void nonEmptyDirectoryWithForceIsAccepted(@TempDir Path tmp) throws IOException {
        Path out = Files.createDirectory(tmp.resolve("out"));
        Path existing = Files.createFile(out.resolve("already-here.txt"));

        // force tolerates the existing entries and leaves them untouched (overwrite is the caller's
        // job, page by page).
        assertThatCode(() -> OutputDirs.prepare(out, true)).doesNotThrowAnyException();
        assertThat(Files.exists(existing)).isTrue();
    }

    @Test
    void utilityClassHasNoInstances() throws ReflectiveOperationException {
        Constructor<OutputDirs> ctor = OutputDirs.class.getDeclaredConstructor();
        assertThat(ctor.canAccess(null)).isFalse();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
