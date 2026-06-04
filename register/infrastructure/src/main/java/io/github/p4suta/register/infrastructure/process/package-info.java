/**
 * Process and filesystem-scratch plumbing for the adapters. The thin {@link
 * io.github.p4suta.register.infrastructure.process.NativeTools} adapter maps register's pipeline
 * tools ({@code pdfimages}/{@code pdfinfo}/{@code jbig2}) onto the cross-app {@code
 * io.github.p4suta.shared.process} plumbing (its {@code ToolPath} resolution, {@code ProcessRunner}
 * run, and {@code Tasks} fan-out), passing register's per-tool {@code -Dregister.<tool>.path}
 * override keys and re-mapping the shared layer's neutral failures to register's error model; the
 * binary {@code jbig2} stdout capture stays raw here. {@link
 * io.github.p4suta.register.infrastructure.process.TempDirs} owns the scratch-directory helpers.
 * {@code ProcessBuilder} is confined to this layer; ArchUnit pins it.
 */
@NullMarked
package io.github.p4suta.register.infrastructure.process;

import org.jspecify.annotations.NullMarked;
