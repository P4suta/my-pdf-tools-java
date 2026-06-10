import io.github.p4suta.gradle.dist.bundleQpdf

plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    id("p4suta.distribution-conventions")
    application
}

// The composition root and runnable artifact (`pdfbook`): Main installs the fatal handler and
// delegates to the CLI, which wires the Source/Stage/Sink adapters into PipelineRunner. The one
// module that sees both :pipeline:application and :pipeline:infrastructure. Runs via installDist,
// and ships as a self-contained jpackage app-image through p4suta.distribution-conventions — the
// same uniform native-staging register/despeckle/tate now share.
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

    // The benchmark fixture generator (test sources, never shipped — mirroring register's
    // createSamplePdf) draws synthetic scan pages with PDFBox directly.
    testImplementation(libs.pdfbox)
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
    // Pin the jar name to pdfbook-<version>.jar so jpackage's --main-jar can find it in lib/.
    archivesName = "pdfbook"
}

// ---- Self-contained distribution (`just package`) ----------------------------------------------
// build/dist-jpackage/pdfbook/ — the flagship CLI as a Docker-free, JDK-free app-image. pdfbook's
// single-pass pipeline (extract -> despeckle -> register -> RTL spread) reaches for Leptonica (FFM,
// via the registrar), pdfimages/pdfinfo (extraction), and the optional qpdf linearize. It does NOT
// use jbig2: the register STAGE writes registered TIFF-G4 pages (it never repacks to a lossless-
// JBIG2 PDF the way the standalone `register pipeline` does), and the spread pack embeds those pages
// as CCITT G4. The convention stages each native + its shared-library closure and points the
// launcher at them via the canonical -Dp4suta.<tool>.path keys; installDist's lib/ is the jpackage
// input (this app ships no shadow jar).
selfContainedApp {
    appName = "pdfbook"
    mainClass = "io.github.p4suta.pipeline.Main"
    mainJar = "pdfbook-${project.version}.jar"
    // Identical to despeckle's needs: FFM/NIO (java.base), ImageIO (java.desktop), PDFBox/xmpbox
    // (java.xml), slf4j-simple over j.u.l (java.logging), PDFBox Unsafe (jdk.unsupported), zip-style
    // stream filters (jdk.zipfs). Pinned; `jdeps --print-module-deps` is the floor to check against.
    modules =
        listOf(
            "java.base",
            "java.desktop",
            "java.xml",
            "java.logging",
            "jdk.unsupported",
            "jdk.zipfs",
        )
    // installDist stages launcher + all jars under build/install/pdfbook; jpackage consumes its lib/.
    appArtifacts.from(layout.buildDirectory.dir("install/pdfbook/lib"))
    appArtifacts.builtBy(tasks.named("installDist"))
    pdfRasterTools()
    // qpdf (Fast Web View), per OS: the upstream release zip on Linux/Windows, the Homebrew host tool
    // on macOS. Resolves via the canonical -Dp4suta.qpdf.path. See bundleQpdf. pdfbook bundles no
    // jbig2 (its register stage writes TIFF-G4; the spread pack embeds CCITT G4).
    bundleQpdf(this, libs.versions.qpdf.get())
}

// ---- Stage-level benchmark (see pipeline/docs/perf-baseline.md) ---------------------------------

// Deterministic synthetic scan book for the benchmark: an existing output is reused, so the
// generation cost (a minute at 200 pages × 600 dpi) is paid once. Knob: -Ppages=N (default 200).
tasks.register<JavaExec>("createSampleScan") {
    group = "verification"
    description = "Generate the synthetic bitonal scan book the benchmark converts (cached)"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.p4suta.pipeline.tools.SampleScanGenerator"
    val pages = providers.gradleProperty("pages").getOrElse("200")
    args = listOf("build/test-data/sample-scan-${pages}p.pdf", pages, "600")
}

// Stage-level runtime + memory benchmark (the pdfbook counterpart of tate's benchRuntime): runs the
// installDist launcher in-container with --timings, parses the per-stage breakdown, samples peak
// RSS from /proc, and writes pipeline/docs/perf-baseline.md. Knobs: -Pruns=N (warm runs, default
// 3), -Pjobs=1,8 (comma-separated -j sweep; default auto = the launcher's CPU-count default),
// -Ppages=N (fixture size, default 200), -Pinputs="a.pdf b.pdf" (real books instead of the
// fixture; resolved against the repo root).
tasks.register<JavaExec>("benchPipeline") {
    group = "verification"
    description = "Benchmark pdfbook stage timings + peak memory; writes pipeline/docs/perf-baseline.md"
    dependsOn(tasks.named("installDist"), tasks.named("createSampleScan"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.p4suta.pipeline.tools.PipelineBenchmark"
    workingDir = rootDir
    val runs = providers.gradleProperty("runs").getOrElse("3")
    val jobs = providers.gradleProperty("jobs").getOrElse("auto")
    val pages = providers.gradleProperty("pages").getOrElse("200")
    val extraInputs =
        providers
            .gradleProperty("inputs")
            .orNull
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    val launcher = "pipeline/app/build/install/pdfbook/bin/pdfbook"
    val inputs =
        extraInputs.ifEmpty { listOf("pipeline/app/build/test-data/sample-scan-${pages}p.pdf") }
    args = listOf(launcher, "qpdf", "pipeline/docs/perf-baseline.md", runs, jobs) + inputs
}
