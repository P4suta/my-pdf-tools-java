/**
 * The Foreign Function &amp; Memory binding to the system Leptonica library. {@code Leptonica} is
 * the one unsafe class holding the {@link java.lang.invoke.MethodHandle} cache and library loader;
 * {@link io.github.p4suta.shared.imaging.Pix} is the owning {@link java.lang.AutoCloseable} handle
 * over a native {@code PIX} that all other code works through.
 *
 * <p>Exposes only primitive pixel operations (read, write, the raw {@code findSkew}/{@code
 * rotateOrth}/{@code rotate}, {@code selectBySize}, the boolean and morphological ops). Policy —
 * confidence-gated {@code deskew}, the despeckle keep-condition — lives app-side.
 *
 * <p>No dependency on any other project module: the read-failure path throws a plain {@link
 * java.lang.IllegalStateException} rather than an app domain exception.
 */
@NullMarked
package io.github.p4suta.shared.imaging;

import org.jspecify.annotations.NullMarked;
