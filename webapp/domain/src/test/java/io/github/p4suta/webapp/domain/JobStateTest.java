package io.github.p4suta.webapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStateTest {

    @Test
    void doneAndFailedAreTerminal() {
        assertThat(JobState.DONE.isTerminal()).isTrue();
        assertThat(JobState.FAILED.isTerminal()).isTrue();
    }

    @Test
    void queuedAndRunningAreNotTerminal() {
        assertThat(JobState.QUEUED.isTerminal()).isFalse();
        assertThat(JobState.RUNNING.isTerminal()).isFalse();
    }
}
