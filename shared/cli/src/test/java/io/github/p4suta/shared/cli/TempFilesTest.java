package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TempFilesTest {

    @Test
    void createTempFileMakesAndDeleteRemovesTheFile() throws Exception {
        Path file = TempFiles.createTempFile("tempfiles-test", ".tmp");
        assertThat(Files.exists(file)).isTrue();

        TempFiles.delete(file);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deleteIsIdempotentAndToleratesUnknownPaths(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        Path absent = dir.resolve("never-created.tmp");

        TempFiles.delete(absent); // no exception for a path not created here
        assertThat(Files.exists(absent)).isFalse();

        Path file = TempFiles.createTempFile("tempfiles-test", ".tmp");
        TempFiles.delete(file);
        TempFiles.delete(file); // second delete is a no-op
        assertThat(Files.exists(file)).isFalse();
    }
}
