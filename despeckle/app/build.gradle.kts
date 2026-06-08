import io.github.p4suta.gradle.dist.qpdfBinary

plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    id("p4suta.distribution-conventions")
    application
}

// The composition root: the only module that sees both :application and :infrastructure, so it
// can wire the adapters into the services. Commons CLI + Main live here, plus the ArchUnit
// cross-module rules (their test classpath spans every module). Ships as a self-contained jpackage
// app-image through p4suta.distribution-conventions, the uniform native-staging the apps share.
dependencies {
    implementation(project(":despeckle:domain"))
    implementation(project(":despeckle:port"))
    implementation(project(":despeckle:application"))
    implementation(project(":despeckle:infrastructure"))
    implementation(project(":shared:observability"))
    // The cross-app CLI scaffolding: the shared int/enum/positional/help/usage-error parsers
    // (CliOptionSupport) and the Throwable -> Error[KIND] + sysexits reporter (CliExceptionHandler)
    // the three front ends (DespeckleCli / PipelineCli / TopdfCli) route through.
    implementation(project(":shared:cli"))
    implementation(libs.commons.cli)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.archunit.junit5)
    testImplementation(testFixtures(project(":despeckle:infrastructure")))
}

// FFM downcalls into Leptonica require native access to be enabled. installDist propagates this
// via applicationDefaultJvmArgs; jpackage via --java-options; tests/JavaExec via the convention.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

// The module is :app, but the tool — its launcher, install dir and jar — is "despeckle". Pin both
// names so installDist produces build/install/despeckle/bin/despeckle (the path `just run` execs)
// and the jar is despeckle-<version>.jar (the name jpackage's --main-jar expects).
application {
    mainClass = "io.github.p4suta.despeckle.Main"
    applicationName = "despeckle"
    applicationDefaultJvmArgs = nativeAccessArgs
}

base {
    archivesName = "despeckle"
}

// ---- Self-contained distribution (`just package`) ----------------------------------------------
// build/dist-jpackage/despeckle/ — the despeckle CLI as a Docker-free, JDK-free app-image. Its
// pipeline reaches for Leptonica (FFM; also the WebP encoder via libwebp in Leptonica's closure),
// pdfimages/pdfinfo/jbig2 (extraction + lossless-JBIG2 repack), and the optional qpdf linearize +
// img2webp flip-book. The convention stages each native + its shared-library closure and points the
// launcher at them via the canonical -Dp4suta.<tool>.path keys; installDist's lib/ is the jpackage
// input (this app ships no shadow jar).
selfContainedApp {
    appName = "despeckle"
    mainClass = "io.github.p4suta.despeckle.Main"
    mainJar = "despeckle-${project.version}.jar"
    // FFM/NIO (java.base), AWT+ImageIO report rendering (java.desktop), PDFBox/xmpbox (java.xml),
    // slf4j-simple over j.u.l (java.logging), PDFBox Unsafe (jdk.unsupported), zip-style stream
    // filters (jdk.zipfs). Pinned; `jdeps --print-module-deps` is the floor to check against.
    modules =
        listOf(
            "java.base",
            "java.desktop",
            "java.xml",
            "java.logging",
            "jdk.unsupported",
            "jdk.zipfs",
        )
    appArtifacts.from(layout.buildDirectory.dir("install/despeckle/lib"))
    appArtifacts.builtBy(tasks.named("installDist"))
    hostLibrary("leptonica", linux = "liblept.so.5", windows = "libleptonica-6.dll", macos = "libleptonica.6.dylib")
    hostTool("pdfimages")
    hostTool("pdfinfo")
    hostTool("jbig2")
    // Optional flip-book assembler (libwebp); bundled when the toolchain has it, skipped otherwise
    // (its absence only skips the WebP flip-book at runtime).
    optionalHostTool("img2webp")
    // qpdf (Fast Web View): Linux/Windows fetch the upstream release zip (kept as a self-contained
    // bin/+lib/ subtree for its RPATH); macOS has no upstream binary, so qpdf comes from the
    // Homebrew prefix as a flat host tool. Either way it resolves via -Dp4suta.qpdf.path.
    if (org.gradle.internal.os.OperatingSystem
            .current()
            .isMacOsX
    ) {
        hostTool("qpdf")
    } else {
        qpdfZip.from(qpdfBinary(libs.versions.qpdf.get()))
    }
}
