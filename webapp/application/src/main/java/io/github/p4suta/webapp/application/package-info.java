/**
 * The web feature's framework-free use cases. {@link
 * io.github.p4suta.webapp.application.Conversions} accepts an upload, queues a job, runs the
 * conversion task (mark RUNNING, drive the engine, route progress to the publisher, record the
 * terminal state), and answers status/result lookups; {@link
 * io.github.p4suta.webapp.application.JobReaper} removes expired jobs. Both are driven through the
 * {@code :webapp:port} interfaces, so they are unit-tested with fakes and contain no Spring or I/O.
 */
@NullMarked
package io.github.p4suta.webapp.application;

import org.jspecify.annotations.NullMarked;
