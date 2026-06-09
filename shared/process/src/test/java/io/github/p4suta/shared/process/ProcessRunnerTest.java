package io.github.p4suta.shared.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs({OS.LINUX, OS.MAC})
final class ProcessRunnerTest {

    @Test
    void successfulProcessReportsExitZeroAndElapsed() throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(List.of("true"), Duration.ofSeconds(10));
        assertThat(result.exitCode()).isZero();
        assertThat(result.elapsed()).isNotNull();
        assertThat(result.elapsed().isNegative()).isFalse();
    }

    @Test
    void capturesStdoutSeparatelyFromStderr() throws Exception {
        // Prove the streams are NOT merged: stdout and stderr land in their own fields.
        ProcessRunner.Result result =
                ProcessRunner.run(
                        List.of("sh", "-c", "echo to-out; echo to-err 1>&2"),
                        Duration.ofSeconds(10));
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("to-out").doesNotContain("to-err");
        assertThat(result.stderr()).contains("to-err").doesNotContain("to-out");
    }

    @Test
    void unacceptableNonZeroExitThrowsWithStderr() {
        // A bare false (exit 1) with no acceptable codes is a failure carrying the captured stderr.
        assertThatThrownBy(
                        () ->
                                ProcessRunner.run(
                                        List.of("sh", "-c", "echo boom 1>&2; exit 1"),
                                        Duration.ofSeconds(10)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exit code 1")
                .hasMessageContaining("boom");
    }

    @Test
    void acceptableNonZeroExitReturnsInsteadOfThrowing() throws Exception {
        // exit 3 is returned, not thrown, when in the acceptable set.
        ProcessRunner.Result result =
                ProcessRunner.run(List.of("sh", "-c", "exit 3"), Duration.ofSeconds(10), Set.of(3));
        assertThat(result.exitCode()).isEqualTo(3);
    }

    @Test
    void exitOutsideTheAcceptableSetStillThrows() {
        // The set tolerates 3 only; a 1 is still unacceptable and throws.
        assertThatThrownBy(
                        () ->
                                ProcessRunner.run(
                                        List.of("sh", "-c", "exit 1"),
                                        Duration.ofSeconds(10),
                                        Set.of(3)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exit code 1");
    }

    @Test
    void slowProcessIsKilledAndReportedAsTimeout() {
        assertThatThrownBy(() -> ProcessRunner.run(List.of("sleep", "5"), Duration.ofMillis(100)))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void floodingStdoutBeyondThePipeBufferDoesNotDeadlock() throws Exception {
        // Regression: the child writes far more to stdout than an OS pipe buffer holds (Linux
        // default ~64KB, Windows ~32-64KB). The old "waitFor() then readAllBytes()" deadlocked —
        // the child blocked on a full pipe, never exited, and the wait tripped the timeout. With
        // output redirected to a file there is no pipe to fill, so it completes and is captured in
        // full. 300000 'x' (NUL from /dev/zero translated) comfortably exceeds the buffer.
        ProcessRunner.Result result =
                ProcessRunner.run(
                        List.of("sh", "-c", "head -c 300000 /dev/zero | tr '\\0' x"),
                        Duration.ofSeconds(10));
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).hasSize(300000);
    }

    @Test
    void floodingStderrBeyondThePipeBufferDoesNotDeadlock() throws Exception {
        // Same flood on the other pipe: exit 0 with a huge stderr must be captured, not deadlock.
        ProcessRunner.Result result =
                ProcessRunner.run(
                        List.of("sh", "-c", "head -c 300000 /dev/zero | tr '\\0' x 1>&2"),
                        Duration.ofSeconds(10));
        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).hasSize(300000);
    }

    @Test
    void missingBinaryFailsWithIoException() {
        assertThatThrownBy(
                        () ->
                                ProcessRunner.run(
                                        List.of("/definitely/not/a/binary"),
                                        Duration.ofSeconds(10)))
                .isInstanceOf(IOException.class);
    }
}
