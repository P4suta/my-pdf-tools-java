import io.github.p4suta.gradle.dist.qpdfBinary

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
    hostLibrary("leptonica", linux = "liblept.so.5", windows = "libleptonica-6.dll", macos = "liblept.5.dylib")
    hostTool("pdfimages")
    hostTool("pdfinfo")
    // qpdf (Fast Web View) — optional: its absence degrades to a no-op, still a valid PDF.
    qpdfZip.from(qpdfBinary(libs.versions.qpdf.get()))
}
