package io.github.p4suta.shared.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class StdinSourceTest {

    @Test
    void drainsStdinIntoNamedTempThenDeletesIt() throws Exception {
        var in = new ByteArrayInputStream("INPUT".getBytes(StandardCharsets.UTF_8));
        AtomicReference<Path> temp = new AtomicReference<>();
        AtomicReference<String> seen = new AtomicReference<>();

        StdinSource.withStdin(
                in,
                "p4suta-in",
                ".bin",
                p -> {
                    temp.set(p);
                    seen.set(Files.readString(p));
                });

        assertThat(seen.get()).isEqualTo("INPUT");
        assertThat(temp.get().getFileName().toString()).startsWith("p4suta-in").endsWith(".bin");
        // The temp file must not survive the call.
        assertThat(Files.exists(temp.get())).isFalse();
    }

    @Test
    void deletesTempEvenWhenActionThrows() throws Exception {
        var in = new ByteArrayInputStream("INPUT".getBytes(StandardCharsets.UTF_8));
        AtomicReference<Path> temp = new AtomicReference<>();

        assertThatThrownBy(
                        () ->
                                StdinSource.withStdin(
                                        in,
                                        "p4suta-in",
                                        ".bin",
                                        p -> {
                                            temp.set(p);
                                            throw new IOException("boom");
                                        }))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");

        assertThat(Files.exists(temp.get())).isFalse();
    }
}
