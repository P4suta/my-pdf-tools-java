plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    id("p4suta.distribution-conventions")
    application
    alias(libs.plugins.shadow)
}

// The composition root and runnable artifact: Main installs the fatal handler and delegates to the
// CLI, which wires the infrastructure adapters into the application services (the one module that
// sees both :application and :infrastructure). It owns the Apache Commons CLI front end and the
// self-contained, Docker-free jpackage app-image distribution (jlink + shadow + staged natives,
// all via p4suta.distribution-conventions).
dependencies {
    // The composition root assembles the whole hexagon. Declaring each module directly (rather than
    // leaning on transitive edges) also anchors the ArchUnit layer definitions in this module's
    // test, so a future change to a module's dependencies can never make a layer go silently empty.
    implementation(project(":register:application"))
    implementation(project(":register:infrastructure"))
    implementation(project(":shared:observability"))
    implementation(project(":register:port"))
    implementation(project(":register:domain"))
    // The cross-app CLI scaffolding: the shared int/double/enum/positional/help/usage-error parsers
    // (CliOptionSupport) and the Throwable -> Error[KIND] + sysexits reporter (CliExceptionHandler)
    // the two front ends route through.
    implementation(project(":shared:cli"))
    // The CLI front end (the one layer allowed to write to System.out/err) lives here.
    implementation(libs.commons.cli)
    // The CLI logs through the SLF4J facade; the binding (slf4j-simple) is added on the runnable
    // app's runtime only (and the test runtime, via test-conventions). The library modules compile
    // against the facade alone.
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    // TestImages (infrastructure's published fixture) builds the synthetic pages the E2E tests drive.
    testImplementation(testFixtures(project(":register:infrastructure")))
    // The E2E tests read the registered output images back through the shared Pix handle (read /
    // width / height / resolution / writePng). :infrastructure depends on :shared:imaging only as an
    // `implementation` edge, which is not transitive on this module's test compile classpath, so the
    // test sources need their own (test-only) edge to the imaging island.
    testImplementation(project(":shared:imaging"))
    // The PDF pipeline E2E tests synthesize a scan PDF and read the registered output back with
    // PDFBox; it is a test-only dependency here (production PDFBox use is confined to :infrastructure).
    testImplementation(libs.pdfbox)
}

// The one place native access is granted to the launched app; run, test and JavaExec inherit it.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

application {
    mainClass = "io.github.p4suta.register.Main"
    // Keep the installDist launcher named `register` (matching the jpackage `--name register`);
    // otherwise the application plugin defaults the launcher name to the subproject name, `app`.
    applicationName = "register"
    applicationDefaultJvmArgs = nativeAccessArgs
}

// register-all.jar (no version in the name) is the stable --main-jar handle the jpackage launcher
// references; mergeServiceFiles keeps the PDFBox/ImageIO SPI and slf4j ServiceLoader registrations
// that a naive shade would clobber.
tasks.shadowJar {
    archiveBaseName = "register"
    archiveClassifier = "all"
    archiveVersion = ""
    mergeServiceFiles()
}

// ---- Self-contained distribution (`just package`) ----------------------------------------------
// build/dist-jpackage/register/ — a PDF -> PDF tool needing neither Docker, a JDK, nor
// poppler/leptonica on the host. The convention bundles a trimmed JRE (jlink), the shaded jar, and
// the natives the pipeline reaches for — the pdfimages/pdfinfo/jbig2 binaries plus the Leptonica
// library FFM loads, each with its transitive shared-library closure — and points the launcher at
// them through the canonical -Dp4suta.<tool>.path keys.
selfContainedApp {
    appName = "register"
    mainClass = "io.github.p4suta.register.Main"
    mainJar = "register-all.jar"
    // Pinned, not jdeps-derived: `jdeps --print-module-deps` is the floor we check against, but an
    // explicit set keeps a dependency bump from silently shrinking the runtime. java.desktop is
    // mandatory (PDFBox' image XObject path touches java.awt.image / ColorModel / ImageIO);
    // java.xml backs xmpbox's XMP packet; jdk.zipfs / jdk.unsupported cover PDFBox stream filters +
    // sun.misc.Unsafe in transitive deps. FFM itself is in java.base.
    modules =
        listOf(
            "java.base",
            "java.desktop",
            "java.logging",
            "java.xml",
            "jdk.unsupported",
            "jdk.zipfs",
        )
    appArtifacts.from(tasks.shadowJar)
    pdfRasterTools()
    hostTool("jbig2")
}
