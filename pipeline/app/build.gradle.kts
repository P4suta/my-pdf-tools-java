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
