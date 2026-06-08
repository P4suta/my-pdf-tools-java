import io.github.p4suta.gradle.dist.NativePlatform
import io.github.p4suta.gradle.dist.SelfContainedAppExtension
import io.github.p4suta.gradle.dist.StageNativesTask
import io.github.p4suta.gradle.dist.StageQpdfTask
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.internal.os.OperatingSystem

// The ONE place the monorepo builds a self-contained jpackage app-image. An app applies this plugin
// and declares what it is + which natives it needs (see `selfContainedApp { }`); everything below —
// jlink, native closure staging, jpackage, and the uniform runtime `-D` wiring — is derived. This
// replaces the ~150 lines of jlink/jpackage/staging that register, despeckle, pipeline and
// tate-yoko-pdf each carried as near-identical copies, and unifies their OS coverage behind the
// `NativePlatform` / `NativeSource` strategies.
plugins {
    java
}

val dist = extensions.create("selfContainedApp", SelfContainedAppExtension::class)

// ---- JDK tools via the project's Java toolchain ------------------------------------------------
// jlink/jpackage ship in the same JDK the toolchain selects (Liberica Full in the dev image, which
// carries the jmods they consume). Resolving them off the toolchain launcher — not the raw java.home
// of whatever JVM runs Gradle — keeps the distribution pinned to the same JDK as the compile/test
// toolchain and is the modern, toolchain-correct way to reach them.
val toolchainLauncher = javaToolchains.launcherFor(java.toolchain)

fun jdkTool(tool: String): Provider<String> =
    toolchainLauncher.map { launcher ->
        val exe = if (OperatingSystem.current().isWindows) "$tool.exe" else tool
        launcher.metadata.installationPath.file("bin/$exe").asFile.absolutePath
    }

// ---- Output locations --------------------------------------------------------------------------
// jlink/jpackage refuse to write into a pre-existing directory while Gradle eagerly creates declared
// output dirs, so each tool's PARENT is the declared output and the tool writes a fixed child, reset
// by a Delete task (config-cache-safe, unlike project.delete in a doFirst).
val jreOutputParent = layout.buildDirectory.dir("dist-jre")
val jreImageDir = jreOutputParent.map { it.dir("runtime") }
val nativesDir = layout.buildDirectory.dir("dist-natives/flat")
val qpdfDir = layout.buildDirectory.dir("dist-natives/qpdf")
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")
val jpackageOutputParent = layout.buildDirectory.dir("dist-jpackage")

// ---- jlink: a trimmed JRE ----------------------------------------------------------------------
val cleanJreImage = tasks.register<Delete>("cleanJreImage") { delete(jreImageDir) }

val jreImage =
    tasks.register<Exec>("jreImage") {
        group = "distribution"
        description = "jlink a trimmed JRE into build/dist-jre/runtime"
        dependsOn(cleanJreImage)
        val modules = dist.modules.get()
        commandLine(
            jdkTool("jlink").get(),
            "--add-modules",
            modules.joinToString(","),
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=zip-9",
            "--output",
            jreImageDir.get().asFile.absolutePath,
        )
        inputs.property("modules", modules.joinToString(","))
        outputs.dir(jreOutputParent)
    }

// ---- Native staging ----------------------------------------------------------------------------
val stageNatives =
    tasks.register<StageNativesTask>("stageNatives") {
        group = "distribution"
        description = "Stage host native tools + libraries and their closure into dist-natives/flat"
        tools.set(dist.tools)
        librarySonames.set(dist.libraries.map { it.values.toList() })
        nativePrefix.set(providers.gradleProperty("p4suta.nativePrefix"))
        outDir.set(nativesDir)
    }

val stageQpdf =
    tasks.register<StageQpdfTask>("stageQpdf") {
        group = "distribution"
        description = "Stage the per-OS qpdf binary into dist-natives/qpdf"
        qpdfZip.from(dist.qpdfZip)
        outDir.set(qpdfDir)
    }

val stageJpackageInput =
    tasks.register<Sync>("stageJpackageInput") {
        group = "distribution"
        description = "Assemble jars + natives into build/jpackage-input for jpackage --input"
        from(dist.appArtifacts)
        from(stageNatives.flatMap { it.outDir }) { into("natives") }
        from(stageQpdf.flatMap { it.outDir }) { into("natives/qpdf") }
        into(jpackageInputDir)
    }

// ---- jpackage: the app-image -------------------------------------------------------------------
val cleanJpackageImage =
    tasks.register<Delete>("cleanJpackageImage") {
        delete(dist.appName.map { jpackageOutputParent.get().dir(it).asFile })
    }

val jpackageImage =
    tasks.register<Exec>("jpackageImage") {
        group = "distribution"
        description = "jpackage the self-contained app-image into build/dist-jpackage/<appName>"
        dependsOn(jreImage, stageJpackageInput, cleanJpackageImage)

        val platform = NativePlatform.current()
        val rawVersion = project.version.toString()
        // macOS maps --app-version onto CFBundleVersion, whose first component must be >= 1; the
        // project version is 0.x, so substitute a valid placeholder for the bundle metadata only
        // (the app-image contents are identical across OSes).
        val appVersion =
            if (OperatingSystem.current().isMacOsX &&
                (rawVersion.substringBefore('.').toIntOrNull() ?: 0) < 1
            ) {
                "1.0.0"
            } else {
                rawVersion
            }

        // The uniform runtime wiring: one canonical `p4suta.<tool>.path` per native, all pointing
        // under $APPDIR/natives. $APPDIR reaches jpackage (and the launcher .cfg) as the literal
        // token — Exec uses no shell — and the launcher expands it to the app dir at run time. The
        // Kotlin backslash only stops Kotlin string interpolation.
        val javaOptions =
            buildList {
                add("--enable-native-access=ALL-UNNAMED")
                add("-XX:MaxRAMPercentage=75.0")
                dist.tools.get().forEach {
                    add("-Dp4suta.$it.path=\$APPDIR/natives/${platform.executableName(it)}")
                }
                dist.libraries.get().forEach { (logical, soname) ->
                    add("-Dp4suta.$logical.path=\$APPDIR/natives/$soname")
                }
                // qpdf bundled from its release zip (Linux/Windows) lives in its own self-contained
                // subtree, because the upstream qpdf binary's RPATH ($ORIGIN/../lib) needs the
                // bin/+lib/ layout preserved. When qpdf instead comes from a toolchain prefix as a
                // host tool (macOS / Homebrew), it is staged flat and already covered by the tool
                // loop above, so this archive-layout key is emitted only when a qpdf zip is wired.
                if (dist.qpdfZip.from.isNotEmpty()) {
                    add("-Dp4suta.qpdf.path=\$APPDIR/natives/qpdf/bin/${platform.executableName("qpdf")}")
                }
                addAll(dist.extraJvmOptions.get())
            }

        val args =
            buildList {
                add(jdkTool("jpackage").get())
                addAll(listOf("--type", "app-image"))
                addAll(listOf("--name", dist.appName.get()))
                addAll(listOf("--input", jpackageInputDir.get().asFile.absolutePath))
                addAll(listOf("--main-jar", dist.mainJar.get()))
                addAll(listOf("--main-class", dist.mainClass.get()))
                addAll(listOf("--runtime-image", jreImageDir.get().asFile.absolutePath))
                addAll(listOf("--dest", jpackageOutputParent.get().asFile.absolutePath))
                addAll(listOf("--app-version", appVersion))
                javaOptions.forEach { addAll(listOf("--java-options", it)) }
                // On Windows, --win-console makes the launcher a console app so stdout/stderr reach
                // the terminal; logging is stderr-only (slf4j-simple). Other OSes need no flag.
                if (OperatingSystem.current().isWindows) {
                    add("--win-console")
                }
            }
        commandLine(args)

        inputs.dir(jreImageDir)
        inputs.dir(jpackageInputDir)
        outputs.dir(jpackageOutputParent)
    }

tasks.register("package") {
    group = "distribution"
    description = "Build the self-contained jpackage app-image (build/dist-jpackage/<appName>)"
    dependsOn(jpackageImage)
}
