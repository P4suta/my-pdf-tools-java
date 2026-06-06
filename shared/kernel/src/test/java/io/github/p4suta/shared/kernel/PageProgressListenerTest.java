package io.github.p4suta.shared.kernel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class PageProgressListenerTest {

    @Test
    void noOpSwallowsTheCallbackWithoutFailing() {
        // The default listener must accept any call and do nothing — services delegate to it when
        // no progress is requested.
        assertDoesNotThrow(() -> PageProgressListener.NO_OP.onPage(1, 1));
    }

    @Test
    void aLambdaImplementationReceivesTheReportedCounts() {
        int[] seen = new int[2];
        PageProgressListener listener =
                (done, total) -> {
                    seen[0] = done;
                    seen[1] = total;
                };

        listener.onPage(3, 7);

        assertArrayEquals(new int[] {3, 7}, seen);
    }
}
