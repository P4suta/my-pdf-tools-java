import io.github.p4suta.gradle.dist.qpdfBinary

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    id("p4suta.distribution-conventions")
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
    implementation(libs.commons.cli)
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

tasks.shadowJar {
    archiveBaseName = "tate-yoko-pdf"
    archiveClassifier = "all"
    archiveVersion = ""
    mergeServiceFiles()
}

// ---- Self-contained distribution (jlink + bundled qpdf + jpackage) -----------------------------
// tate has no FFM native dependency; its only native piece is qpdf (Fast Web View linearisation),
// self-downloaded per OS from qpdf's GitHub releases. The convention stages it under natives/qpdf
// (preserving the upstream bin/+lib/ layout its own RPATH needs) and points the launcher at it via
// the canonical -Dp4suta.qpdf.path key. qpdf's absence degrades to a no-op (still a valid PDF), so
// the smoke check below converts a real sample to confirm the bundled qpdf actually runs.
selfContainedApp {
    appName = "tate-yoko-pdf"
    mainClass = "io.github.p4suta.tateyokopdf.Main"
    mainJar = "tate-yoko-pdf-all.jar"
    // java.base + everything PDFBox / slf4j-simple / HttpClient need at runtime:
    //  - java.desktop: PDFBox' PDDocument <clinit> touches java.awt.image.Raster / ColorModel.
    //  - jdk.crypto.ec: TLS cipher suites used by Java HttpClient.
    //  - jdk.unsupported: sun.misc.Unsafe used by some transitive deps.
    //  - jdk.zipfs: PDFBox uses zip-style stream filters.
    modules =
        listOf(
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
    appArtifacts.from(tasks.shadowJar)
    // The per-OS qpdf release zip (empty on macOS, where upstream ships no Darwin binary).
    qpdfZip.from(qpdfBinary(libs.versions.qpdf.get()))
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
    // qpdf now lives in the uniform natives/qpdf subtree the distribution convention stages.
    val qpdfBin = "app/build/dist-jpackage/tate-yoko-pdf/lib/app/natives/qpdf/bin/qpdf"
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
