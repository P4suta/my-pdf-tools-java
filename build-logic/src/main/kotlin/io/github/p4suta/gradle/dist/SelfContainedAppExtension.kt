package io.github.p4suta.gradle.dist

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.internal.os.OperatingSystem

/**
 * The declarative surface of [the self-contained distribution convention][applying it via
 * `p4suta.distribution-conventions`]. An app states what it is — its launcher name, main class, the
 * jlink module set, the jars to put in the image — and which native dependencies it reaches for; the
 * convention derives the jlink + native-staging + jpackage pipeline and the runtime `-D` wiring.
 *
 * Native requirements are logical, not paths: [hostTool]/[hostLibrary] name a tool or library and
 * [NativeSource] resolves it to a concrete seed for the build host, [NativePlatform] bundles its
 * closure, and the convention emits `-Dp4suta.<name>.path=$APPDIR/natives/…` so the packaged app
 * resolves the bundled binary through the cross-app canonical key (see `ToolPath.canonicalKey`).
 */
abstract class SelfContainedAppExtension {

    /** The launcher / image name (e.g. `pdfbook`), also the `jpackage --name`. */
    abstract val appName: Property<String>

    /** The fully-qualified main class jpackage launches. */
    abstract val mainClass: Property<String>

    /** The jar file name jpackage's `--main-jar` references (e.g. `register-all.jar`). */
    abstract val mainJar: Property<String>

    /** The explicit jlink module set (pinned, not jdeps-derived; jdeps is the floor to check against). */
    abstract val modules: ListProperty<String>

    /** What lands in the jpackage `--input` root: the shadow jar, or installDist's `lib/` contents. */
    abstract val appArtifacts: ConfigurableFileCollection

    /** Logical executable tools to bundle (e.g. `pdfimages`, `pdfinfo`, `jbig2`). */
    abstract val tools: ListProperty<String>

    /**
     * Optional tools: bundled when present in the source, silently skipped when not, so a runtime-
     * optional helper (e.g. `img2webp`) never fails the build on an OS whose toolchain omits it.
     */
    abstract val optionalTools: ListProperty<String>

    /**
     * Libraries to bundle, keyed by logical name and mapped to the on-disk file name for the build
     * host (the loader requests that exact name). Populated by [hostLibrary], which resolves the
     * per-OS name at configuration time — the build host is the jpackage target, so the current OS's
     * name is the right one.
     */
    abstract val libraries: MapProperty<String, String>

    /** The per-OS qpdf release zip to bundle under `natives/qpdf` (empty = no qpdf in this image). */
    abstract val qpdfZip: ConfigurableFileCollection

    /** Extra JVM options for the launcher, on top of native-access + the heap percentage. */
    abstract val extraJvmOptions: ListProperty<String>

    /** Declare an executable tool dependency the image bundles and points `-Dp4suta.<name>.path` at. */
    fun hostTool(name: String) {
        tools.add(name)
    }

    /** Declare a runtime-optional tool: bundled if the source has it, skipped (not fatal) if not. */
    fun optionalHostTool(name: String) {
        optionalTools.add(name)
    }

    /**
     * Declare a library dependency the image bundles, with its per-OS on-disk file name. [logical]
     * is the canonical-key stem (so the launcher gets `-Dp4suta.<logical>.path`); the OS arguments
     * are the file name the loader requests on each platform — e.g. for Leptonica:
     * `liblept.so.5` (Linux soname), `libleptonica-6.dll` (MSYS2 mingw64), `liblept.5.dylib`
     * (Homebrew). The real name is kept (never renamed to a canonical stem): on Linux the bundled
     * `jbig2` links Leptonica by its `DT_NEEDED` soname and resolves it among the co-located natives
     * via `$ORIGIN`, so a rename would dangle that lookup.
     *
     * The name is resolved for the build host here, at configuration time, because the build host is
     * the jpackage target. An OS left null fails fast when that OS is the host (rather than silently
     * shipping a broken image).
     */
    fun hostLibrary(logical: String, linux: String? = null, windows: String? = null, macos: String? = null) {
        val os = OperatingSystem.current()
        val name =
            when {
                os.isLinux -> linux
                os.isWindows -> windows
                os.isMacOsX -> macos
                else -> null
            }
                ?: throw GradleException(
                    "hostLibrary(\"$logical\"): no library file name declared for ${os.name}",
                )
        libraries.put(logical, name)
    }
}
