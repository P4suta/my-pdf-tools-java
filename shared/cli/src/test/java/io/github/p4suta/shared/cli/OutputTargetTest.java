package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OutputTargetTest {

    @Test
    void fileTargetHandsBackTheDestinationPath(@TempDir Path tmp) throws Exception {
        Path dest = tmp.resolve("out.bin");
        AtomicReference<Path> handed = new AtomicReference<>();

        OutputTarget.file(dest)
                .write(
                        p -> {
                            handed.set(p);
                            Files.writeString(p, "PAYLOAD");
                        });

        // A file target writes straight to the destination — no temp indirection, no stream-out.
        assertThat(handed.get()).isEqualTo(dest);
        assertThat(Files.readString(dest)).isEqualTo("PAYLOAD");
    }

    @Test
    void stdoutTargetStreamsResultThenDeletesTemp() throws Exception {
        var sink = new ByteArrayOutputStream();
        AtomicReference<Path> temp = new AtomicReference<>();

        OutputTarget.stdout("p4suta-out", ".bin")
                .write(
                        p -> {
                            temp.set(p);
                            Files.writeString(p, "STREAMED");
                        },
                        sink);

        assertThat(sink.toString(StandardCharsets.UTF_8)).isEqualTo("STREAMED");
        // The bridge temp file must not survive the stream-out.
        assertThat(Files.exists(temp.get())).isFalse();
        assertThat(temp.get().getFileName().toString()).startsWith("p4suta-out").endsWith(".bin");
    }

    @Test
    void stdoutTargetDeletesTempEvenWhenActionThrows() throws Exception {
        var sink = new ByteArrayOutputStream();
        AtomicReference<Path> temp = new AtomicReference<>();

        assertThatThrownBy(
                        () ->
                                OutputTarget.stdout("p4suta-out", ".bin")
                                        .write(
                                                p -> {
                                                    temp.set(p);
                                                    throw new IOException("boom");
                                                },
                                                sink))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");

        assertThat(Files.exists(temp.get())).isFalse();
        assertThat(sink.size()).isZero();
    }
}
