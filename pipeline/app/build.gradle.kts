plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    application
}

// The composition root and runnable artifact (`pdfbook`): Main installs the fatal handler and
// delegates to the CLI, which wires the Source/Stage/Sink adapters into PipelineRunner. The one
// module that sees both :pipeline:application and :pipeline:infrastructure. Runs via installDist in
// the dev image (which carries Leptonica + pdfimages/jbig2/qpdf); a self-contained jpackage image
// is a follow-up (the same native-staging register already does).
dependencies {
    implementation(project(":pipeline:application"))
    implementation(project(":pipeline:infrastructure"))
    implementation(project(":pipeline:domain"))
    implementation(project(":pipeline:port"))

    // CLI option parsing and sink construction use tate's reading-direction / first-page /
    // memory-mode / metadata value types.
    implementation(project(":tate-yoko-pdf:domain"))

    // The shared progress event vocabulary + JSONL codec: the --progress-file sink writes these.
    implementation(project(":shared:progress"))

    // The shared CLI scaffolding: positional/glob input resolver, batch driver, exit-code reporter,
    // and the int/enum/positional parse helpers — the one layer allowed to write to System.out/err.
    implementation(project(":shared:cli"))
    implementation(project(":shared:observability"))
    implementation(libs.commons.cli)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
}

// The one place native access is granted to the launched app; run, test and JavaExec inherit it.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

application {
    mainClass = "io.github.p4suta.pipeline.Main"
    // Keep the installDist launcher named `pdfbook` rather than the subproject name `app`.
    applicationName = "pdfbook"
    applicationDefaultJvmArgs = nativeAccessArgs
}

base {
    // Pin the jar name to pdfbook-<version>.jar so jpackage's --main-jar below can find it.
    archivesName = "pdfbook"
}

// ---- Distribution: jlink + jpackage app-image ------------------------------
// Mirrors :despeckle:app — build/dist-jpackage/pdfbook/ with a launcher and a trimmed JRE (jlink),
// the app-image's --input being installDist's lib/. Native tools (pdfimages / pdfinfo / jbig2 / qpdf
// + libleptonica) are a documented PATH runtime dependency, NOT bundled (the dev container supplies
// them); the image is self-contained for the JVM side only.

val jpackageAppName = "pdfbook"

// Minimal module set (verify with `jdeps --print-module-deps`); identical needs to despeckle:
// FFM/NIO (java.base), ImageIO (java.desktop), PDFBox/xmpbox (java.xml), slf4j-simple over j.u.l
// (java.logging), PDFBox Unsafe (jdk.unsupported) and zip-style stream filters (jdk.zipfs).
val bundledModules =
    listOf(
        "java.base",
        "java.desktop",
        "java.xml",
        "java.logging",
        "jdk.unsupported",
        "jdk.zipfs",
    )

// CC-safe tool resolution: capture java.home (or JAVA_HOME) as a Provider at configuration time.
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

// jlink/jpackage refuse a pre-existing output dir; declare each tool's parent as the output and
// reset the fixed child with a Delete task, config-cache-safely.
val jreOutputParent = layout.buildDirectory.dir("dist-jre")
val jreImageDir = jreOutputParent.map { it.dir("runtime") }
val jpackageOutputParent = layout.buildDirectory.dir("dist-jpackage")
val installInputDir = layout.buildDirectory.dir("install/$jpackageAppName/lib")

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

tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Run jpackage to build the pdfbook app-image under build/dist-jpackage/"
    dependsOn(jreImage, tasks.named("installDist"), cleanJpackageImage)

    val mainJarName = "$jpackageAppName-${project.version}.jar"

    commandLine =
        listOf(
            toolPath("jpackage").get(),
            "--type",
            "app-image",
            "--name",
            jpackageAppName,
            "--input",
            installInputDir.get().asFile.absolutePath,
            "--main-jar",
            mainJarName,
            "--main-class",
            "io.github.p4suta.pipeline.Main",
            "--runtime-image",
            jreImageDir.get().asFile.absolutePath,
            "--dest",
            jpackageOutputParent.get().asFile.absolutePath,
            "--java-options",
            "--enable-native-access=ALL-UNNAMED",
            "--java-options",
            "-XX:MaxRAMPercentage=75.0",
        )

    inputs.dir(jreImageDir)
    inputs.dir(installInputDir)
    outputs.dir(jpackageOutputParent)
}
