/**
 * The cross-app shared kernel: the small, framework-free primitives that every p4suta app
 * (register, despeckle, tate-yoko-pdf) would otherwise duplicate. The dpi-based pixel/millimeter
 * conversion ({@link io.github.p4suta.shared.kernel.Resolution}) and the exception-neutral
 * precondition checks ({@link io.github.p4suta.shared.kernel.Validators}). No project dependencies,
 * no third-party runtime library — pure logic the apps reuse unchanged.
 */
@NullMarked
package io.github.p4suta.shared.kernel;

import org.jspecify.annotations.NullMarked;
