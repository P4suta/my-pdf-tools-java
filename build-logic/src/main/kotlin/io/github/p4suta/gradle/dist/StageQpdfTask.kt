package io.github.p4suta.gradle.dist

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

/**
 * Stages the qpdf binary distribution (an Ivy-fetched per-OS release zip) into a self-contained
 * `bin/` + `lib/` subtree, preserving the upstream layout so qpdf's own `$ORIGIN/../lib` RPATH (and
 * the `libqpdf` SONAME it loads) keep resolving. qpdf is staged apart from the flat closure
 * [StageNativesTask] produces precisely because it is self-contained and relocation-sensitive in a
 * way the relinked host tools are not; the convention points `-Dp4suta.qpdf.path` at `bin/qpdf`
 * inside this subtree.
 *
 * Empty-zip tolerant: on a host with no upstream qpdf binary the output stays empty and qpdf
 * resolution falls through to PATH / no-op, exactly as before.
 */
abstract class StageQpdfTask
    @Inject
    constructor(
        private val archives: ArchiveOperations,
        private val fsOps: FileSystemOperations,
    ) : DefaultTask() {

        /** The per-OS qpdf release zip (from the `qpdfBinary` Ivy configuration); may be empty. */
        @get:InputFiles abstract val qpdfZip: ConfigurableFileCollection

        /** The `natives/qpdf` subtree to populate (`bin/qpdf` + `lib/libqpdf.*`). */
        @get:OutputDirectory abstract val outDir: DirectoryProperty

        @TaskAction
        fun stage() {
            val out = outDir.get().asFile
            fsOps.delete { delete(out) }
            out.mkdirs()
            if (qpdfZip.isEmpty) {
                return
            }
            fsOps.copy {
                from(archives.zipTree(qpdfZip.singleFile)) {
                    // Strip the leading `qpdf-<version>/` directory the upstream zip wraps everything
                    // in, so the layout is bin/… + lib/… directly under outDir.
                    eachFile {
                        val segments = relativePath.segments
                        if (segments.isNotEmpty() && segments[0].startsWith("qpdf-")) {
                            relativePath = RelativePath(true, *segments.drop(1).toTypedArray())
                        }
                    }
                    includeEmptyDirs = false
                    // Headers / docs / share data are not needed at runtime.
                    exclude("**/include/**", "**/share/**", "**/doc/**")
                }
                into(out)
            }
            if (OperatingSystem.current().isLinux) {
                pruneRedundantQpdfLib(out)
            }
        }

        /**
         * The upstream zip ships the real library as `libqpdf.so.<major>.<minor>.<patch>` and the
         * SONAME `libqpdf.so.<major>` the loader requests as a symlink to it. Collapse the pair to a
         * single real file under the SONAME: promote the versioned file to the SONAME name (after
         * removing the alias), so the bundle carries the bytes once.
         *
         * A real file — not a preserved symlink — because the downstream Sync and jpackage both
         * dereference symlinks (and a symlink whose target we removed would dangle, breaking qpdf).
         * qpdf's binary NEEDs exactly `libqpdf.so.<major>`, so the promoted file is what it resolves.
         * No-op when no versioned file is present; macOS dylibs and Windows DLLs use other naming, so
         * the regex (hence this prune) is inert there.
         */
        private fun pruneRedundantQpdfLib(out: File) {
            val libDir = out.resolve("lib")
            if (!libDir.isDirectory) {
                return
            }
            val versioned =
                libDir
                    .listFiles { f -> f.name.matches(Regex("""libqpdf\.so\.\d+\.\d+.*""")) }
                    .orEmpty()
                    .firstOrNull()
                    ?: return
            val major = versioned.name.removePrefix("libqpdf.so.").substringBefore('.')
            val soname = libDir.resolve("libqpdf.so.$major")
            if (versioned.name == soname.name) {
                return
            }
            // Remove the SONAME alias (a symlink in the upstream layout, or a duplicate full copy if
            // zipTree already dereferenced it) and rename the real bytes onto the SONAME name.
            Files.deleteIfExists(soname.toPath())
            Files.move(versioned.toPath(), soname.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
