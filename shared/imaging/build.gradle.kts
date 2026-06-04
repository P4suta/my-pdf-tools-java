import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    // The single place native access is granted: FFM downcalls into Leptonica need
    // `--enable-native-access=ALL-UNNAMED` on the (test) JVM, plus `-Xshare:off` for the jacoco-agent
    // CDS warning. Applying this convention is what lets the FFM smoke + functional tests actually run.
    id("p4suta.native-conventions")
}

// The third cross-app shared module: the FFM imaging island. It unions the two duplicated
// Leptonica bindings (register's projection/geometry/deskew/scale set and despeckle's
// size-select/morphology/boolean/counting set) into one Foreign Function & Memory island
// (Leptonica) plus one owning RAII handle (Pix) that exposes only PRIMITIVE pixel ops. Project
// policy (register's confidence-gated deskew, despeckle's keepComponentsLargerThan) stays app-side
// and is layered on these primitives in 3b-2. NO runtime dependency on any other project module and
// NO third-party runtime library — only the conventions' compileOnly jspecify annotations — so the
// read() failure path throws a plain IllegalStateException rather than reaching for an app's
// domain exception.

// Coverage floor. Unlike the pure-logic shared modules (:kernel, :observability at 0.95/0.90), this
// module is an FFM/exec island: the Leptonica binding is nothing but downcall wrappers whose
// defensive `catch (Throwable)` branches cannot be exercised without a corrupt native library, so
// it is EXCLUDED from the floor exactly as both original infrastructure modules did. The floor then
// tracks only the genuinely unit-testable Pix RAII wrapper, held to the same realistic infra-like
// 0.75 line / 0.60 branch the despeckle :infrastructure module uses — FFM happy paths are
// branch-poor (mostly straight-line downcalls), so a domain-grade 0.90 branch floor would be
// dishonest here.
val coverageExcludes =
    listOf(
        // Despeckle's robust pattern: the Leptonica binding now lives directly in
        // io.github.p4suta.shared.imaging (no `leptonica` sub-package), so the directory-less glob
        // is what matches its compiled path.
        "**/Leptonica.class",
    )

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
