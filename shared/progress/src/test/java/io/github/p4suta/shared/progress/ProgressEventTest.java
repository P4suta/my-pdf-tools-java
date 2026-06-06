package io.github.p4suta.shared.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProgressEventTest {

    @Test
    void runStartedRejectsNegativeStageCount() {
        assertThatThrownBy(() -> new ProgressEvent.RunStarted(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stageCount must be non-negative: -1");
    }

    @Test
    void stageStartedRejectsNegativeIndex() {
        assertThatThrownBy(() -> new ProgressEvent.StageStarted("despeckle", -1, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("index must be non-negative: -1");
    }

    @Test
    void stageStartedRejectsNegativeStageCount() {
        assertThatThrownBy(() -> new ProgressEvent.StageStarted("despeckle", 0, -4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stageCount must be non-negative: -4");
    }

    @Test
    void pageProcessedRejectsNegativeDone() {
        assertThatThrownBy(() -> new ProgressEvent.PageProcessed("register", -1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("done must be non-negative: -1");
    }

    @Test
    void pageProcessedRejectsNegativeTotal() {
        assertThatThrownBy(() -> new ProgressEvent.PageProcessed("register", 1, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("total must be non-negative: -5");
    }

    @Test
    void exposesItsComponents() {
        ProgressEvent.PageProcessed event = new ProgressEvent.PageProcessed("despeckle", 12, 300);
        assertThat(event.stage()).isEqualTo("despeckle");
        assertThat(event.done()).isEqualTo(12);
        assertThat(event.total()).isEqualTo(300);
    }
}
