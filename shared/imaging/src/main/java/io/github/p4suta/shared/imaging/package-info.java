/**
 * The cross-app FFM imaging island: the single Foreign Function &amp; Memory binding to the system
 * Leptonica library that every p4suta app (register, despeckle, tate-yoko-pdf) would otherwise
 * duplicate. {@code Leptonica} is the one unsafe class that holds the {@link
 * java.lang.invoke.MethodHandle} cache and library loader; {@link
 * io.github.p4suta.shared.imaging.Pix} is the owning {@link java.lang.AutoCloseable} RAII handle
 * over a native {@code PIX} that all other code works through.
 *
 * <p>This module unions the two previously-disjoint Leptonica islands — register's projection
 * profiles / geometry / deskew / scale set and despeckle's size-select / morphology / boolean /
 * counting set — into one binding. It exposes only PRIMITIVE pixel operations (read, write, the raw
 * {@code findSkew}/{@code rotateOrth}/{@code rotate}, the raw {@code selectBySize}, the boolean and
 * morphological ops). Project POLICY — register's confidence-gated {@code deskew} and despeckle's
 * {@code keepComponentsLargerThan} — deliberately stays app-side and is layered on these primitives
 * by the per-app infrastructure.
 *
 * <p>No third-party runtime library and no dependency on any other project module: the read-failure
 * path throws a plain {@link java.lang.IllegalStateException} rather than an app domain exception.
 */
@NullMarked
package io.github.p4suta.shared.imaging;

import org.jspecify.annotations.NullMarked;
