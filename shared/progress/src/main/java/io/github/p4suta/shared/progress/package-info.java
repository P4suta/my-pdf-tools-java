/**
 * The framework-free vocabulary of progress and lifecycle events a pdfbook conversion emits ({@link
 * io.github.p4suta.shared.progress.ProgressEvent}), the {@link
 * io.github.p4suta.shared.progress.ProgressSink} an emitter writes them to, and the
 * one-object-per-line JSONL wire codec ({@link
 * io.github.p4suta.shared.progress.JsonlProgressCodec}) that carries them across a process
 * boundary. A pdfbook subprocess writes events to its {@code --progress-file}; a front end reads
 * them back. Pure value types and a dependency-free codec, no I/O (the file/stream plumbing lives
 * with each caller).
 */
@NullMarked
package io.github.p4suta.shared.progress;

import org.jspecify.annotations.NullMarked;
