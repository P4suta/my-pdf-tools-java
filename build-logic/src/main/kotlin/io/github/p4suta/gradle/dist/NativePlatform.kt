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
 * is register's proven native-staging behavior, lifted behind the interface.
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
 * macOS: an `otool -L` closure, with each bundled member's dependencies rewritten to
 * `@loader_path/<name>` via `install_name_tool` and then re-signed ad-hoc. `/usr/lib` + `/System`
 * are left to the host. `@loader_path` is the Mach-O analogue of Linux's `$ORIGIN` — it resolves a
 * dependency relative to the loading module's own directory, so a flat co-located bundle is
 * self-resolving without the Windows preload dance. The re-sign is mandatory on arm64: modifying a
 * Mach-O with `install_name_tool` invalidates its (linker-/ad-hoc) code signature, and the loader
 * refuses an invalid signature on Apple Silicon, so each rewritten file gets a fresh ad-hoc
 * signature (`codesign --force --sign -`).
 */
object MacPlatform : NativePlatform {

    // An `otool -L` dependency line: a leading-whitespace install name followed by " (compatibility…".
    private val dependencyLine = Regex("""^\s+(\S+)\s+\(""")

    override fun closure(
        execOps: ExecOperations,
        seeds: List<File>,
        searchDirs: List<File>,
    ): List<File> {
        val byName = LinkedHashMap<String, File>()
        val queue = ArrayDeque(seeds)
        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            if (byName.containsKey(file.name)) {
                continue
            }
            byName[file.name] = file
            dependencies(execOps, file).forEach { install ->
                resolveInstallName(install, searchDirs)?.let { dep ->
                    if (!byName.containsKey(dep.name)) {
                        queue.add(dep)
                    }
                }
            }
        }
        return byName.values.toList()
    }

    override fun shouldBundle(lib: File): Boolean =
        !lib.path.startsWith("/usr/lib/") && !lib.path.startsWith("/System/")

    override fun establishRuntimeDiscovery(execOps: ExecOperations, bundleDir: File) {
        val staged = bundleDir.listFiles().orEmpty()
        val present = staged.map { it.name }.toSet()
        staged.forEach { file ->
            if (file.name.endsWith(".dylib")) {
                execOps.exec {
                    commandLine("install_name_tool", "-id", "@loader_path/${file.name}", file.absolutePath)
                    isIgnoreExitValue = true
                }
            }
            dependencies(execOps, file).forEach { install ->
                val base = install.substringAfterLast('/')
                if (base in present && install != "@loader_path/$base") {
                    execOps.exec {
                        commandLine(
                            "install_name_tool", "-change", install, "@loader_path/$base", file.absolutePath,
                        )
                        isIgnoreExitValue = true
                    }
                }
            }
            // install_name_tool invalidated the signature; re-sign ad-hoc (required on arm64).
            execOps.exec {
                commandLine("codesign", "--force", "--sign", "-", file.absolutePath)
                isIgnoreExitValue = true
            }
        }
    }

    override fun executableName(logical: String): String = logical

    // The dependency install names from `otool -L`, skipping the first line (the file's own path).
    private fun dependencies(execOps: ExecOperations, file: File): List<String> {
        val out = ByteArrayOutputStream()
        execOps.exec {
            commandLine("otool", "-L", file.absolutePath)
            standardOutput = out
            isIgnoreExitValue = true
        }
        return out.toString()
            .lineSequence()
            .drop(1)
            .mapNotNull { dependencyLine.find(it)?.groupValues?.get(1) }
            .toList()
    }

    // Map an install name to a bundleable file, or null for a host (system) library to leave alone.
    private fun resolveInstallName(install: String, searchDirs: List<File>): File? {
        if (install.startsWith("/usr/lib/") || install.startsWith("/System/")) {
            return null
        }
        if (install.startsWith("/")) {
            val absolute = File(install)
            return if (absolute.isFile) absolute else locate(absolute.name, searchDirs)
        }
        // @rpath / @loader_path / @executable_path prefixed — resolve the base name in the prefix.
        return locate(install.substringAfterLast('/'), searchDirs)
    }

    private fun locate(name: String, searchDirs: List<File>): File? =
        searchDirs.firstNotNullOfOrNull { dir -> File(dir, name).takeIf { it.isFile } }
}
