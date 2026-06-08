package io.github.p4suta.gradle.dist

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Stages an app's native tools and libraries — plus their transitive shared-library closure — into
 * one flat directory, each member relinked so co-located siblings resolve without any environment
 * the launcher would have to set. The OS-specific work (closure walk, bundle-vs-host policy,
 * runtime-discovery relink) is delegated to [NativePlatform]; seed resolution (logical name ->
 * concrete host/prefix file) to [NativeSource]. The task itself only orchestrates and copies, so it
 * is identical on every OS and stays configuration-cache safe (injected [ExecOperations] /
 * [FileSystemOperations], no `Project` access at execution time).
 */
abstract class StageNativesTask
    @Inject
    constructor(
        private val execOps: ExecOperations,
        private val fsOps: FileSystemOperations,
    ) : DefaultTask() {

        /** Executable tool names to bundle (e.g. `pdfimages`, `pdfinfo`, `jbig2`). */
        @get:Input abstract val tools: ListProperty<String>

        /** Runtime-optional tool names: bundled when the source has them, skipped when not. */
        @get:Input abstract val optionalTools: ListProperty<String>

        /** Library sonames to bundle (e.g. `liblept.so.5`); the FFM-loaded libraries and friends. */
        @get:Input abstract val librarySonames: ListProperty<String>

        /** The toolchain prefix from `-Pp4suta.nativePrefix`, when the host source needs one. */
        @get:[Input Optional] abstract val nativePrefix: Property<String>

        /** The flat output directory of co-located, relinked natives. */
        @get:OutputDirectory abstract val outDir: DirectoryProperty

        @TaskAction
        fun stage() {
            val out = outDir.get().asFile
            fsOps.delete { delete(out) }
            out.mkdirs()

            val toolNames = tools.get()
            val optionalNames = optionalTools.get()
            val libraryNames = librarySonames.get()
            // Nothing to stage (e.g. tate-yoko-pdf, whose only native is the separately-staged
            // qpdf): leave an empty dir and don't resolve a source — so an app with no host
            // tools/libraries needs no toolchain prefix on Windows/macOS.
            if (toolNames.isEmpty() && optionalNames.isEmpty() && libraryNames.isEmpty()) {
                return
            }

            val platform = NativePlatform.current()
            val source = NativeSource.forHost(nativePrefix.orNull)

            val optionalSeeds =
                optionalNames.mapNotNull { name ->
                    try {
                        source.resolveTool(name)
                    } catch (notPresent: RuntimeException) {
                        logger.lifecycle("optional native tool '$name' not found in source; skipping")
                        null
                    }
                }
            val seeds =
                toolNames.map { source.resolveTool(it) } +
                    optionalSeeds +
                    libraryNames.map { source.resolveLibrary(it) }
            val seedNames = seeds.map { it.name }.toSet()
            val searchDirs = source.searchDirs()

            platform.closure(execOps, seeds, searchDirs).forEach { src ->
                // Seeds are always bundled; their dependency closure is filtered by the OS policy.
                if (src.name !in seedNames && !platform.shouldBundle(src)) {
                    return@forEach
                }
                // copyTo reads through symlinks, so the destination is a regular file under the
                // requested soname holding the real bytes. It does NOT carry POSIX mode, so re-grant
                // the executable bit the tools (pdfimages/pdfinfo/jbig2) need — else ProcessBuilder
                // hits "Permission denied" launching them. Libraries stay non-exec.
                val dst = File(out, src.name)
                src.copyTo(dst, overwrite = true)
                dst.setWritable(true)
                if (src.canExecute()) {
                    dst.setExecutable(true, false)
                }
            }

            platform.establishRuntimeDiscovery(execOps, out)
        }
    }
