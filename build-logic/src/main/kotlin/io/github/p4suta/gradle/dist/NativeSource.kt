package io.github.p4suta.gradle.dist

import java.io.File
import org.gradle.internal.os.OperatingSystem

/**
 * Resolves an app's native requirements (a tool by name, a library by its host file name) to the
 * concrete seed file the closure walk starts from, for the host the build runs on. The shape of the
 * answer is the same everywhere — an absolute [File] — but where it comes from is the OS's package
 * layout:
 *
 *  - [HostPathSource] (Linux): the dev image's apt-installed `/usr/bin` + `/usr/lib`, plus the
 *    from-source `jbig2` in `/usr/local/bin`. This is register's original seed model. The system
 *    loader resolves dependencies itself (via `ldd`), so [searchDirs] is empty.
 *  - [ToolchainPrefixSource] (Windows MSYS2 `mingw64`, macOS Homebrew): everything comes out of the
 *    prefix passed via `-Pp4suta.nativePrefix`. The closure walk must be told where the prefix's
 *    libraries live ([searchDirs]) because the Windows/macOS tools name dependencies without a path.
 *
 * Separating "where seeds come from" ([NativeSource]) from "how their closure is bundled and made
 * self-resolving" ([NativePlatform]) keeps the two axes that vary independently — package layout and
 * loader semantics — from entangling.
 */
sealed interface NativeSource {

    /** The absolute path of executable [tool], or fail with the locations tried. */
    fun resolveTool(tool: String): File

    /** The absolute path of the library file named [fileName], or fail with the locations tried. */
    fun resolveLibrary(fileName: String): File

    /**
     * Directories the dependency-closure walk should search for a named library. Empty on Linux
     * (`ldd` returns absolute paths, so no search is needed); the prefix's lib/bin dirs on the
     * toolchain-prefix sources, where `objdump`/`otool` yield bare names to locate.
     */
    fun searchDirs(): List<File>

    companion object {
        /**
         * The seed source for the build host. [prefix] is `-Pp4suta.nativePrefix` — required on
         * Windows/macOS (the MSYS2 `mingw64` / Homebrew prefix), ignored on Linux.
         */
        fun forHost(prefix: String?): NativeSource {
            val os = OperatingSystem.current()
            if (os.isLinux) {
                return HostPathSource
            }
            val resolved =
                prefix
                    ?: throw IllegalStateException(
                        "native bundling on ${os.name} requires -Pp4suta.nativePrefix=<toolchain"
                            + " prefix> (the MSYS2 mingw64 dir on Windows, `brew --prefix` on macOS)",
                    )
            return ToolchainPrefixSource(File(resolved))
        }
    }
}

/**
 * The Linux host source: tools off `PATH` (then the standard `/usr/local/bin`, `/usr/bin`, `/bin`),
 * libraries off the multiarch lib directories. Equivalent to the absolute seeds register hard-codes,
 * resolved rather than pinned so a relocation does not silently break.
 */
object HostPathSource : NativeSource {

    override fun resolveTool(tool: String): File {
        val dirs =
            buildList {
                System.getenv("PATH")?.split(File.pathSeparator)?.forEach {
                    if (it.isNotEmpty()) add(it)
                }
                addAll(listOf("/usr/local/bin", "/usr/bin", "/bin"))
            }
        dirs.forEach { dir ->
            val candidate = File(dir, tool)
            if (candidate.canExecute()) {
                return candidate
            }
        }
        throw IllegalStateException("native tool '$tool' not found; tried $dirs")
    }

    override fun resolveLibrary(fileName: String): File {
        val triplet =
            when (System.getProperty("os.arch", "")) {
                "amd64", "x86_64" -> "x86_64-linux-gnu"
                "aarch64", "arm64" -> "aarch64-linux-gnu"
                else -> null
            }
        val candidates =
            buildList {
                if (triplet != null) {
                    add("/usr/lib/$triplet/$fileName")
                    add("/lib/$triplet/$fileName")
                }
                add("/usr/lib/$fileName")
                add("/usr/local/lib/$fileName")
            }
        candidates.forEach { path ->
            val candidate = File(path)
            if (candidate.exists()) {
                return candidate
            }
        }
        throw IllegalStateException("native library '$fileName' not found; tried $candidates")
    }

    override fun searchDirs(): List<File> = emptyList()
}

/**
 * A toolchain-prefix source: everything is taken from one install prefix — MSYS2's `mingw64` on
 * Windows (tools and DLLs both under `bin/`), Homebrew's prefix on macOS (tools under `bin/`,
 * dylibs under `lib/`). The prefix is provided by the CI provisioning step via
 * `-Pp4suta.nativePrefix`.
 */
class ToolchainPrefixSource(private val prefix: File) : NativeSource {

    private val windows = OperatingSystem.current().isWindows

    private val binDir = File(prefix, "bin")
    private val libDir = File(prefix, "lib")

    override fun resolveTool(tool: String): File {
        val exe = if (windows) "$tool.exe" else tool
        val candidate = File(binDir, exe)
        if (candidate.canExecute() || candidate.isFile) {
            return candidate
        }
        throw IllegalStateException("native tool '$exe' not found under $binDir")
    }

    override fun resolveLibrary(fileName: String): File {
        // mingw64 keeps DLLs in bin/; Homebrew keeps dylibs in lib/.
        val dirs = if (windows) listOf(binDir) else listOf(libDir, binDir)
        dirs.forEach { dir ->
            val candidate = File(dir, fileName)
            if (candidate.isFile) {
                return candidate
            }
        }
        throw IllegalStateException("native library '$fileName' not found under $dirs")
    }

    override fun searchDirs(): List<File> = if (windows) listOf(binDir) else listOf(libDir, binDir)
}
