package io.github.p4suta.shared.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressSinkTest {

    @Test
    void noOpDiscardsEvents() {
        // Nothing observable — just exercise the discard so it cannot silently start throwing.
        ProgressSink.NO_OP.emit(new ProgressEvent.RunCompleted());
        ProgressSink.NO_OP.emit(new ProgressEvent.StageCompleted("x"));
    }

    @Test
    void aCapturingSinkReceivesEveryEvent() {
        List<ProgressEvent> seen = new ArrayList<>();
        ProgressSink sink = seen::add;

        sink.emit(new ProgressEvent.RunStarted(2));
        sink.emit(new ProgressEvent.RunCompleted());

        assertThat(seen)
                .containsExactly(new ProgressEvent.RunStarted(2), new ProgressEvent.RunCompleted());
    }
}
