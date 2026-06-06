package io.github.p4suta.webapp.app;

import io.github.p4suta.webapp.port.QueueStats;

/**
 * A fixed {@link QueueStats} snapshot for tests. The record components are named to match the
 * interface methods, so the generated accessors implement it with no body.
 */
record FakeQueueStats(int queued, int active, int capacity, int remainingCapacity, long completed)
        implements QueueStats {}
