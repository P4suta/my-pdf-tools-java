/**
 * Process-execution utilities for the infrastructure adapters: resolving the external native tools
 * ({@code pdfimages}, {@code pdfinfo}, {@code jbig2}, {@code qpdf}) via {@code
 * -Ddespeckle.<tool>.path} then {@code PATH}, and launching them with output capture and timeouts.
 * Discovery is delegated to the cross-app {@code io.github.p4suta.shared.process.ToolPath} island
 * (the despeckle property keys stay the per-app parameter it is passed); the launch helpers that
 * must return raw binary output or propagate a tagged {@code DespeckleException} stay local, while
 * the text-only {@code qpdf} call site uses the shared {@code ProcessRunner}. This is the "how" of
 * shelling out to a {@link java.util.List} of command-line arguments, not a domain intent, so it
 * stays an infrastructure-internal helper rather than a port.
 */
@NullMarked
package io.github.p4suta.despeckle.infrastructure.process;

import org.jspecify.annotations.NullMarked;
