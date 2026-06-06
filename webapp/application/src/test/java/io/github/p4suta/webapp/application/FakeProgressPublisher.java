package io.github.p4suta.webapp.application;

import io.github.p4suta.shared.progress.ProgressEvent;
import io.github.p4suta.webapp.domain.JobId;
import io.github.p4suta.webapp.port.ProgressPublisher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records published events per job and the ids whose streams were closed. */
final class FakeProgressPublisher implements ProgressPublisher {

    private final Map<JobId, List<ProgressEvent>> published = new LinkedHashMap<>();
    final List<JobId> closed = new ArrayList<>();

    @Override
    public void publish(JobId id, ProgressEvent event) {
        published.computeIfAbsent(id, key -> new ArrayList<>()).add(event);
    }

    @Override
    public void close(JobId id) {
        closed.add(id);
    }

    List<ProgressEvent> eventsFor(JobId id) {
        return published.getOrDefault(id, List.of());
    }
}
