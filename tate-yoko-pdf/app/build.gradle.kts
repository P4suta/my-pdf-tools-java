import java.nio.file.Files
import javax.inject.Inject

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":tate-yoko-pdf:domain"))
    implementation(project(":tate-yoko-pdf:port"))
    implementation(project(":tate-yoko-pdf:application"))
    implementation(project(":tate-yoko-pdf:infrastructure"))
    // The shared CLI scaffolding: tate now consumes io.github.p4suta.shared.cli.* (InputResolver,
    // OutputTarget, StdinSource, IoPathAction, CliExceptionHandler, BatchDriver) instead of its own
    // tate-local copies. It `api`-exposes commons-cli + :shared:kernel + :shared:observability, so
    // those flow transitively onto this app's compile classpath through this one declaration.
    implementation(project(":shared:cli"))
    // The shared error kernel: the CLI names ErrorCategory directly (mapping.kind().name() and the
    // OUT_OF_MEMORY comparison in CliExceptionHandler). Neither :domain nor :shared:observability
    // exposes the kernel transitively (both declare it `implementation`), so the app declares it.
    implementation(project(":shared:kernel"))
    implementation(project(":shared:observability"))
    implementation("commons-cli:commons-cli:1.11.0")
    // The CLI logs through the SLF4J facade; the binding (slf4j-simple) is added on the runnable
    // app's runtime only (and the test runtime, via test-conventions), matching register/despeckle.
    // Replaces the former logback-classic binding so all three apps share one slf4j backend.
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    // veraPDF greenfield validator — independently confirms the emitted file is genuinely PDF/A-2b,
    // not merely tagged as such. Test-only; brings its own (non-PDFBox) parser.
    testImplementation("org.verapdf:validation-model:1.30.1")
    testImplementation(testFixtures(project(":tate-yoko-pdf:infrastructure")))
    testImplementation(testFixtures(project(":tate-yoko-pdf:application")))
}

application {
    mainClass = "io.github.p4suta.tateyokopdf.Main"
}

repositories {
    // qpdf official GitHub releases — Ivy URL repository for fetching the Fast Web View
    // post-processor binary as a regular Gradle dependency. Bundled by stageJpackageInput below.
    ivy {
        name = "qpdf-releases"
        url = uri("https://github.com/qpdf/qpdf/releases/download/")
        patternLayout {
            artifact("v[revision]/qpdf-[revision]-[classifier].[ext]")
        }
        metadataSources { artifact() }
        content { includeGroup("com.github.qpdf") }
    }
}

val qpdfVersion = "12.3.2"

// Per-host classifier: jpackage only emits images for the host OS. macOS is intentionally absent —
// upstream qpdf has no Darwin binary, so QpdfLinearizer's noOp/PATH fallback applies.
val qpdfBinary by configurations.creating { isCanBeConsumed = false }

dependencies {
    val hostOs =
        org.gradle.internal.os.OperatingSystem
            .current()
    val qpdfCoords: String? =
        when {
            hostOs.isLinux -> "com.github.qpdf:qpdf:$qpdfVersion:bin-linux-x86_64@zip"
            hostOs.isWindows -> "com.github.qpdf:qpdf:$qpdfVersion:mingw64@zip"
            else -> null
        }
    qpdfCoords?.let { qpdfBinary(it) }
}

tasks.shadowJar {
    archiveBaseName = "tate-yoko-pdf"
    archiveClassifier = "all"
    archiveVersion = ""
    mergeServiceFiles()
}

tasks.register<JavaExec>("createSamplePdf") {
    group = "verification"
    description = "Generate a sample multi-page PDF for manual / smoke testing"
    // SamplePdfGenerator lives in :infrastructure, which is on app's runtime classpath.
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "io.github.p4suta.tateyokopdf.infrastructure.pdfbox.tools.SamplePdfGenerator"
    args = listOf("build/test-data/sample.pdf", "4")
}

// Runtime + memory benchmark (the Java successor to scripts/bench-runtime.sh). The harness spawns
// the bundled app-image launcher as a child process, timing it with System.nanoTime and sampling
// peak RSS from /proc/<pid>/status — pure Java, no GNU time. Override the warm-run count with
// -Pruns=N and add files with -Pinputs="a.pdf b.pdf".
tasks.register<JavaExec>("benchRuntime") {
    group = "verification"
    description = "Benchmark conversion runtime + peak memory; writes docs/perf-runtime.md"
    dependsOn("jpackageImage", "createSamplePdf")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "io.github.p4suta.tateyokopdf.tools.RuntimeBenchmark"
    workingDir = rootDir
    val runs = providers.gradleProperty("runs").getOrElse("5")
    val extraInputs =
        providers
            .gradleProperty("inputs")
            .orNull
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    val launcher = "app/build/dist-jpackage/tate-yoko-pdf/bin/tate-yoko-pdf"
    val qpdfBin = "app/build/dist-jpackage/tate-yoko-pdf/lib/app/bin/qpdf"
    val inputs = extraInputs.ifEmpty { listOf("app/build/test-data/sample.pdf") }
    args =
        listOf(launcher, qpdfBin, "docs/perf-runtime.md", runs, "MaxRAMPercentage=75.0") + inputs
}

// Cross-platform CLI smoke test (the Java successor to the per-OS shell / PowerShell CI steps):
// convert sample.pdf through the built app-image and assert the output carries the %PDF magic. The
// OS-specific launcher path is resolved here so one task works on every host.
tasks.register<JavaExec>("smokeCheck") {
    group = "verification"
    description = "Convert a sample PDF through the built app-image and assert the output is a PDF"
    dependsOn("jpackageImage", "createSamplePdf")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "io.github.p4suta.tateyokopdf.tools.SmokeCheck"
    // Project-relative: projectDir is this :tate-yoko-pdf:app dir, so the launcher/input/output
    // paths drop the leading `app/`. This works regardless of rootDir (which is the tate project
    // root when standalone but /workspace in the monorepo).
    workingDir = project.projectDir
    val os =
        org.gradle.internal.os.OperatingSystem
            .current()
    val launcher =
        when {
            os.isMacOsX -> "build/dist-jpackage/tate-yoko-pdf.app/Contents/MacOS/tate-yoko-pdf"
            os.isWindows -> "build/dist-jpackage/tate-yoko-pdf/tate-yoko-pdf.exe"
            else -> "build/dist-jpackage/tate-yoko-pdf/bin/tate-yoko-pdf"
        }
    args =
        listOf(
            launcher,
            "build/test-data/sample.pdf",
            "build/test-data/jpackage-out.pdf",
        )
}

// ---- Distribution: jlink + jpackage app-image ------------------------------
// Produces build/dist-jpackage/tate-yoko-pdf/ with a launcher, a trimmed JRE (jlink), and the
// shadow jar. jlink/jpackage are invoked directly (Beryx 1.13.x is incompatible with Gradle 9.x).

val jpackageAppName = "tate-yoko-pdf"
val bundledModules =
    listOf(
        // java.base + everything PDFBox / slf4j-simple / HttpClient need at runtime.
        // - java.desktop: PDFBox' PDDocument <clinit> touches java.awt.image.Raster / ColorModel.
        // - jdk.crypto.ec: TLS cipher suites used by Java HttpClient.
        // - jdk.unsupported: sun.misc.Unsafe used by some transitive deps.
        // - jdk.zipfs: PDFBox uses zip-style stream filters.
        "java.base",
        "java.desktop",
        "java.naming",
        "java.management",
        "java.logging",
        "java.net.http",
        "java.sql",
        "java.xml",
        "jdk.crypto.ec",
        "jdk.unsupported",
        "jdk.zipfs",
    )

val javaHomeProvider: Provider<String> =
    providers.systemProperty("java.home").orElse(providers.environmentVariable("JAVA_HOME"))

fun toolPath(tool: String): Provider<String> =
    javaHomeProvider.map { home ->
        val exe =
            if (org.gradle.internal.os.OperatingSystem
                    .current()
                    .isWindows
            ) {
                "$tool.exe"
            } else {
                tool
            }
        "$home/bin/$exe"
    }

// jlink/jpackage refuse to write into a pre-existing directory; Gradle eagerly creates declared
// output dirs. So we declare each tool's *parent* as the output and have the tool write a fixed
// child inside it, with separate Delete tasks to reset state config-cache-safely.
val jreOutputParent = layout.buildDirectory.dir("dist-jre")
val jreImageDir = jreOutputParent.map { it.dir("runtime") }
val jpackageOutputParent = layout.buildDirectory.dir("dist-jpackage")
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")

val hostIsLinux =
    org.gradle.internal.os.OperatingSystem
        .current()
        .isLinux
val hostIsWindows =
    org.gradle.internal.os.OperatingSystem
        .current()
        .isWindows

abstract class StageJpackageInput : DefaultTask() {
    @get:InputFiles abstract val shadowJar: ConfigurableFileCollection

    @get:InputFiles abstract val qpdfZip: ConfigurableFileCollection

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Input abstract val fixLinuxSoSymlink: Property<Boolean>

    @get:Inject abstract val archives: ArchiveOperations

    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun run() {
        files.sync {
            from(shadowJar)
            if (!qpdfZip.isEmpty) {
                from(archives.zipTree(qpdfZip.singleFile)) {
                    eachFile {
                        val segs = relativePath.segments
                        if (segs.isNotEmpty() && segs[0].startsWith("qpdf-")) {
                            relativePath =
                                RelativePath(true, *segs.drop(1).toTypedArray())
                        }
                    }
                    includeEmptyDirs = false
                    // headers / docs not needed at runtime
                    exclude("**/include/**", "**/share/**", "**/doc/**")
                }
            }
            into(outputDir)
        }
        if (fixLinuxSoSymlink.get()) {
            val libDir =
                outputDir
                    .get()
                    .asFile
                    .toPath()
                    .resolve("lib")
            val link = libDir.resolve("libqpdf.so.30")
            val target = libDir.resolve("libqpdf.so.30.3.2")
            if (Files.isRegularFile(link) && Files.isRegularFile(target)) {
                Files.delete(link)
                Files.createSymbolicLink(link, libDir.relativize(target))
            }
        }
    }
}

val stageJpackageInput =
    tasks.register<StageJpackageInput>("stageJpackageInput") {
        group = "distribution"
        description = "Stage shadow jar and (on Linux/Windows) qpdf into jpackage-input/"
        shadowJar.from(tasks.shadowJar.flatMap { it.archiveFile })
        if (hostIsLinux || hostIsWindows) {
            qpdfZip.from(qpdfBinary)
        }
        outputDir.set(jpackageInputDir)
        fixLinuxSoSymlink.set(hostIsLinux)
    }

val cleanJreImage =
    tasks.register<Delete>("cleanJreImage") {
        delete(jreImageDir)
    }

val cleanJpackageImage =
    tasks.register<Delete>("cleanJpackageImage") {
        delete(jpackageOutputParent.map { it.dir(jpackageAppName) })
    }

val jreImage =
    tasks.register<Exec>("jreImage") {
        group = "distribution"
        description = "Run jlink to build a trimmed JRE under build/dist-jre/runtime/"
        dependsOn(cleanJreImage)

        commandLine =
            listOf(
                toolPath("jlink").get(),
                "--add-modules",
                bundledModules.joinToString(","),
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress=zip-9",
                "--output",
                jreImageDir.get().asFile.absolutePath,
            )
        inputs.property("modules", bundledModules.joinToString(","))
        outputs.dir(jreOutputParent)
    }

val jpackageImage =
    tasks.register<Exec>("jpackageImage") {
        group = "distribution"
        description = "Run jpackage to build the app-image under build/dist-jpackage/"
        dependsOn(jreImage, stageJpackageInput, cleanJpackageImage)

        val mainJarName =
            tasks.shadowJar
                .get()
                .archiveFileName
                .get()

        // On Windows, --win-console makes the launcher a console app so stdout/stderr show in the
        // terminal. Other OSes need no flag. Logging is stderr-only (slf4j-simple default); no file log.
        val hostOs =
            org.gradle.internal.os.OperatingSystem
                .current()
        val jpackageArgs =
            listOf(
                toolPath("jpackage").get(),
                "--type",
                "app-image",
                "--name",
                jpackageAppName,
                "--input",
                jpackageInputDir.get().asFile.absolutePath,
                "--main-jar",
                mainJarName,
                "--main-class",
                application.mainClass.get(),
                "--runtime-image",
                jreImageDir.get().asFile.absolutePath,
                "--dest",
                jpackageOutputParent.get().asFile.absolutePath,
                "--app-version",
                project.version.toString(),
                // MaxRAMPercentage adapts heap to the host (and container cgroups) instead of a
                // fixed value. --low-memory additionally spills page streams to disk.
                "--java-options",
                "-XX:MaxRAMPercentage=75.0",
            )
        commandLine = if (hostOs.isWindows) jpackageArgs + "--win-console" else jpackageArgs

        inputs.dir(jreImageDir)
        inputs.dir(jpackageInputDir)
        outputs.dir(jpackageOutputParent)
    }
