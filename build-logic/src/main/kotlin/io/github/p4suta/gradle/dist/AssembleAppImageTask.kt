package io.github.p4suta.gradle.dist

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Assembles the final self-contained image: jpackage's output for THIS app, with each bundled
 * sibling app-image nested under its `$APPDIR/tools/<name>`. Runs AFTER jpackage and writes to its
 * OWN output dir (`build/dist-app`), never back into jpackage's `build/dist-jpackage` — overlapping
 * outputs would defeat Gradle's up-to-date checking and be clobbered by jpackage's clean. Only the
 * apps that declare [SelfContainedAppExtension.bundledApp] use it; the leaf apps' `package` stays on
 * jpackage's own output.
 *
 * The copy is a faithful filesystem walk (symlinks recreated not followed, POSIX mode re-applied) so
 * the jlink launcher keeps its exec bit and the macOS runtime's symlinked frameworks survive — a
 * dereferencing Gradle CopySpec would not. The OS-specific layout (where `$APPDIR` is, the per-OS
 * image-root name) and the macOS outer-bundle re-sign come from [NativePlatform]; the task only
 * orchestrates, staying configuration-cache safe (injected [FileSystemOperations]/[ExecOperations],
 * no `Project` at execution time).
 */
abstract class AssembleAppImageTask
    @Inject
    constructor(
        private val execOps: ExecOperations,
        private val fsOps: FileSystemOperations,
    ) : DefaultTask() {

        /** This app's launcher / image name (`selfContainedApp.appName`). */
        @get:Input abstract val appName: Property<String>

        /** jpackage's output PARENT — `build/dist-jpackage` (it holds the `<imageRoot>` this nests into). */
        @get:InputDirectory abstract val jpackageOutput: DirectoryProperty

        /** The image-root directories of the bundled sibling app-images to nest under `tools/`. */
        @get:InputFiles abstract val bundledImages: ConfigurableFileCollection

        /** `build/dist-app` — the assembled image's OWN output parent (never jpackage's dir). */
        @get:OutputDirectory abstract val outDir: DirectoryProperty

        @TaskAction
        fun assemble() {
            val platform = NativePlatform.current()
            val imageRoot = platform.imageRootName(appName.get())

            val src = jpackageOutput.get().dir(imageRoot).asFile
            require(src.isDirectory) { "assembleAppImage: jpackage image not found: $src" }

            val outParent = outDir.get().asFile
            fsOps.delete { delete(outParent) }
            outParent.mkdirs()
            val dst = File(outParent, imageRoot)
            copyTree(src.toPath(), dst.toPath())

            // Nest each sibling image-root dir under $APPDIR/tools/<its name> (keeping the per-OS .app
            // wrapper). Its name is imageRootName(<sibling>), so the launcher's baked
            // -Dp4suta.<name>.path=$APPDIR/tools/<embeddedLauncherSubpath> points exactly here.
            val toolsDir = File(dst, platform.appDirWithinImage() + "/tools")
            toolsDir.mkdirs()
            bundledImages.forEach { image ->
                require(image.isDirectory) { "assembleAppImage: bundled image not found: $image" }
                copyTree(image.toPath(), File(toolsDir, image.name).toPath())
            }

            // Nesting into a signed bundle invalidated its outer seal (macOS only); re-sign ad-hoc.
            platform.sealEmbeddedImage(execOps, dst)
        }

        // A faithful recursive copy: symlinks recreated (not followed), POSIX mode re-applied — so the
        // jlink launcher's exec bit and the macOS runtime's symlinked frameworks survive intact.
        private fun copyTree(src: Path, dst: Path) {
            Files.walk(src).use { stream ->
                stream.forEach { from ->
                    val to = dst.resolve(src.relativize(from).toString())
                    when {
                        Files.isSymbolicLink(from) -> {
                            Files.createDirectories(to.parent)
                            Files.deleteIfExists(to)
                            Files.createSymbolicLink(to, Files.readSymbolicLink(from))
                        }
                        Files.isDirectory(from) -> Files.createDirectories(to)
                        else -> {
                            Files.createDirectories(to.parent)
                            Files.copy(
                                from,
                                to,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES,
                            )
                            // COPY_ATTRIBUTES does not guarantee the POSIX mode; re-apply it so the
                            // launcher/runtime executables keep their exec bit (no-op on Windows).
                            runCatching {
                                Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from))
                            }
                        }
                    }
                }
            }
        }
    }
