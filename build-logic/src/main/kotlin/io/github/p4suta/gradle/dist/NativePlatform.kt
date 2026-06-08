package io.github.p4suta.gradle.dist

import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations

/**
 * The OS-symmetric strategy for turning a set of native "seed" binaries (the tools and libraries an
 * app reaches for) into a co-located, self-resolving bundle. Three concerns vary by operating
 * system and nothing else does, so they live behind this one interface:
 *
 *  1. [closure] — walk the transitive shared-library dependency graph of the seeds. The mechanism
 *     differs (`ldd` on Linux, recursive `objdump -p` on Windows, `otool -L` on macOS) but the
 *     output is the same: every library the seeds load.
 *  2. [shouldBundle] — the single bundle-vs-host policy. Each OS leaves a different core to the host
 *     loader (glibc + ld-linux on Linux, the System32 OS DLLs on Windows, `/usr/lib` + `/System` on
 *     macOS) and bundles everything else. One predicate, not an inverted exclude/include split.
 *  3. [establishRuntimeDiscovery] — make a launcher that sets no environment resolve every
 *     co-located sibling. Linux rewrites RUNPATH to `$ORIGIN`; macOS rewrites each dependency's
 *     install-name to `@loader_path`; Windows bakes nothing into the binaries (its dependent-DLL
 *     search is extrinsic — the loaded module's own directory plus the process search path), so the
 *     relink is a no-op and discovery is the bundle layout's job.
 *
 * Implementations are stateless and take an injected [ExecOperations] per call so the owning task
 * stays configuration-cache safe (it never reaches back into the `Project` at execution time).
 */
sealed interface NativePlatform {

    /**
     * Every shared library reachable from [seeds] (each an absolute path), the seeds included,
     * de-duplicated by the name the loader requests. [searchDirs] is where to look up a dependency
     * named without a path (the toolchain prefix's lib/bin dirs); empty on Linux, where `ldd`
     * returns absolute paths. Implementations fail fast on an unresolved dependency rather than
     * shipping a bundle that cannot load.
     */
    fun closure(execOps: ExecOperations, seeds: List<File>, searchDirs: List<File>): List<File>

    /**
     * Whether [lib] is copied into the bundle, or left for the host loader to provide. Seeds are
     * always bundled regardless; this governs their dependency closure only.
     */
    fun shouldBundle(lib: File): Boolean

    /**
     * Establish runtime discovery over the flat [bundleDir] of co-located natives, so each member
     * resolves its siblings without any environment set by the launcher.
     */
    fun establishRuntimeDiscovery(execOps: ExecOperations, bundleDir: File)

    /** The on-disk file name of a logical executable ([logical] plus `.exe` on Windows). */
    fun executableName(logical: String): String

    companion object {
        /** The strategy for the host the build runs on (jpackage only emits images for that host). */
        fun current(): NativePlatform {
            val os = OperatingSystem.current()
            return when {
                os.isLinux -> LinuxPlatform
                os.isWindows -> WindowsPlatform
                os.isMacOsX -> MacPlatform
                else ->
                    throw UnsupportedOperationException(
                        "self-contained native bundling is not supported on ${os.name}",
                    )
            }
        }
    }
}

/**
 * Linux: `ldd` closure, RUNPATH rewritten to `$ORIGIN` via `patchelf`. The glibc / dynamic-loader
 * core stays host-resolved — bundling a build-image `ld-linux`/`libc` next to host-loaded objects
 * would put two libcs in one process — so the contract is "host glibc >= build-image glibc". This
 * is register's proven native-staging behaviour, lifted behind the interface.
 */
object LinuxPlatform : NativePlatform {

    // glibc + the dynamic loader: left to the host, never bundled.
    private val hostResolvedPrefixes =
        listOf(
            "libc.so",
            "libm.so",
            "libpthread.so",
            "libdl.so",
            "librt.so",
            "libresolv.so",
            "libgcc_s.so",
            "ld-linux",
            "linux-vdso",
        )

    override fun closure(
        execOps: ExecOperations,
        seeds: List<File>,
        searchDirs: List<File>,
    ): List<File> {
        // Keyed by the SONAME the loader requests (the "name => /path" left side == basename for
        // these libs), so symlink aliases collapse and each object lands under the name its
        // dependents expect. searchDirs is unused: ldd resolves to absolute paths itself.
        val byName = linkedMapOf<String, File>()
        seeds.forEach { seed ->
            require(seed.exists()) { "stageNatives: seed not found: $seed" }
            byName.putIfAbsent(seed.name, seed)
            val out = ByteArrayOutputStream()
            execOps.exec {
                commandLine("ldd", seed.absolutePath)
                standardOutput = out
            }
            out.toString().lineSequence().forEach { line ->
                require(!line.contains("not found")) {
                    "stageNatives: unresolved dependency for $seed -> ${line.trim()}"
                }
                val dep =
                    Regex("""=>\s+(/\S+)""").find(line)?.let { File(it.groupValues[1]) }
                        ?: return@forEach
                byName.putIfAbsent(dep.name, dep)
            }
        }
        return byName.values.toList()
    }

    override fun shouldBundle(lib: File): Boolean =
        hostResolvedPrefixes.none { lib.name.startsWith(it) }

    override fun establishRuntimeDiscovery(execOps: ExecOperations, bundleDir: File) {
        bundleDir.listFiles().orEmpty().forEach { staged ->
            // Co-located siblings resolve via RUNPATH=$ORIGIN without LD_LIBRARY_PATH (the jpackage
            // launcher sets no environment). Executables and libraries alike get the rewrite.
            execOps.exec { commandLine("patchelf", "--set-rpath", "\$ORIGIN", staged.absolutePath) }
        }
    }

    override fun executableName(logical: String): String = logical
}

/**
 * Windows: a recursive `objdump -p` closure over the mingw64 toolchain. Dependencies are named
 * without a path, so each is located in [searchDirs] (the MSYS2 `mingw64/bin`); a name that is NOT
 * found there is a System32 OS DLL and is left to the host. Everything found in the prefix —
 * including the mingw runtime DLLs (`libgcc_s_seh-1`, `libstdc++-6`, `libwinpthread-1`) and the
 * Leptonica/poppler closure — is bundled. No relink: Windows resolves a binary's dependent DLLs
 * from the loaded module's own directory and the process search path, not from a baked-in path.
 *
 * The open question this enables CI to answer: a jpackage app-image launches external tools by
 * absolute path (Windows then searches the tool's own dir for its DLLs ✓), but Leptonica is loaded
 * into the JVM via `System.load`, and whether the JVM's loader searches the loaded DLL's own
 * directory for ITS dependencies is what the Windows real-PDF smoke validates. If co-location
 * proves insufficient, the fallback is a launcher PATH-prepend — not pre-built here.
 */
object WindowsPlatform : NativePlatform {

    private val dllName = Regex("""DLL Name:\s+(\S+)""")

    override fun closure(
        execOps: ExecOperations,
        seeds: List<File>,
        searchDirs: List<File>,
    ): List<File> {
        val objdump = locateObjdump(searchDirs)
        val byName = LinkedHashMap<String, File>()
        val queue = ArrayDeque(seeds)
        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            val key = file.name.lowercase()
            if (byName.containsKey(key)) {
                continue
            }
            byName[key] = file
            val out = ByteArrayOutputStream()
            execOps.exec {
                commandLine(objdump, "-p", file.absolutePath)
                standardOutput = out
                isIgnoreExitValue = true
            }
            out.toString().lineSequence().forEach { line ->
                val dep = dllName.find(line)?.groupValues?.get(1) ?: return@forEach
                if (byName.containsKey(dep.lowercase())) {
                    return@forEach
                }
                // Found in the prefix → bundle it; absent → a System32 OS DLL, host-resolved.
                locate(dep, searchDirs)?.let { queue.add(it) }
            }
        }
        return byName.values.toList()
    }

    // The closure only admits prefix-local files (a System DLL is never located there), so every
    // member is bundled; the policy is expressed by what closure() admits.
    override fun shouldBundle(lib: File): Boolean = true

    override fun establishRuntimeDiscovery(execOps: ExecOperations, bundleDir: File) {
        // No relink: Windows has no RUNPATH. Co-location is the discovery mechanism (validated by CI).
    }

    override fun executableName(logical: String): String = "$logical.exe"

    private fun locateObjdump(searchDirs: List<File>): String {
        searchDirs.forEach { dir ->
            val candidate = File(dir, "objdump.exe")
            if (candidate.canExecute() || candidate.isFile) {
                return candidate.absolutePath
            }
        }
        return "objdump"
    }

    private fun locate(name: String, searchDirs: List<File>): File? {
        searchDirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.name.equals(name, ignoreCase = true)) {
                    return file
                }
            }
        }
        return null
    }
}

/**
 * macOS: an `otool -L` closure with each dependency's install-name rewritten to `@loader_path` via
 * `install_name_tool`, `/usr/lib` + `/System` left to the host. Implemented after the Windows
 * mechanism is proven in CI (`macos-latest` exists and already cross-builds tate, so it is
 * CI-validated, not guessed), to replicate a proven approach rather than ship an untested one.
 */
object MacPlatform : NativePlatform {
    private const val PENDING = "MacPlatform native bundling lands after the Windows leg is proven in CI"

    override fun closure(
        execOps: ExecOperations,
        seeds: List<File>,
        searchDirs: List<File>,
    ): List<File> = throw UnsupportedOperationException(PENDING)

    override fun shouldBundle(lib: File): Boolean = throw UnsupportedOperationException(PENDING)

    override fun establishRuntimeDiscovery(execOps: ExecOperations, bundleDir: File): Unit =
        throw UnsupportedOperationException(PENDING)

    override fun executableName(logical: String): String = logical
}
