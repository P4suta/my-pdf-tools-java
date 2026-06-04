package io.github.p4suta.tateyokopdf.testfixtures;

import io.github.p4suta.tateyokopdf.application.ProgressListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every progress callback in order for test assertions. */
public final class CapturingProgressListener implements ProgressListener {

    public sealed interface Event {
        record Start(int totalSpreads) implements Event {}

        record SpreadComplete(int currentSpread, int totalSpreads) implements Event {}

        record Complete(long elapsedMillis) implements Event {}
    }

    private final List<Event> events = new CopyOnWriteArrayList<>();

    public List<Event> events() {
        return List.copyOf(events);
    }

    @Override
    public void onStart(int totalSpreads) {
        events.add(new Event.Start(totalSpreads));
    }

    @Override
    public void onSpreadComplete(int currentSpread, int totalSpreads) {
        events.add(new Event.SpreadComplete(currentSpread, totalSpreads));
    }

    @Override
    public void onComplete(long elapsedMillis) {
        events.add(new Event.Complete(elapsedMillis));
    }
}
