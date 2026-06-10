import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// Orchestration: the corpus registration service (the two-pass directory walk + worker pool) and the
// PDF -> PDF drivers. Depends on :domain + :port (plus the framework-free :shared:kernel primitives)
// — the Leptonica, PDFBox and pdfimages/jbig2 adapters are injected at the :app composition root, so
// this layer never sees :infrastructure. slf4j-api is the only third-party dependency (the binding is
// bundled in :app).
dependencies {
    implementation(project(":register:domain"))
    implementation(project(":register:port"))

    // The framework-free upper-median primitive (Medians) shared across the apps, used by the
    // auto-paper median scan-size computation.
    implementation(project(":shared:kernel"))

    // The cross-app filesystem-orchestration island: OutputDirs.prepare (output-dir guard with the
    // exact "is not empty; pass --force" message), CorpusFiles.collect (glob-selected, path-sorted
    // corpus walk) and CorpusFiles.mirrorDestination (input-relative output mirroring with a
    // caller-supplied extension). Register passes its own glob/OutputFormat.extension() so this layer
    // imports neither the matcher nor the OutputFormat enum.
    implementation(project(":shared:io"))
    // Tasks.awaitAll(Workers...): the shared fail-fast page fan-out (batch-owned executor,
    // sibling interruption, quiescence before the failure propagates) both registration passes
    // run their per-page work on.
    implementation(project(":shared:process"))
    implementation(libs.slf4j.api)
}

// Coverage floor for the orchestration's own isolated unit tests, driven through hand-written fake
// ports (see Fakes) the same way despeckle:application and tate:application are — so all three apps
// share one application-layer test shape and floor (0.85 line / 0.65 branch). Measured here ~94%
// line / ~84% branch: RegistrationService (empty-corpus, dpi inheritance, auto/explicit paper, the
// reference-null all-centered path, diagnostics, worker-failure propagation), PdfPipelineService
// (missing-input/output-conflict/force guards, explicit-vs-dominant dpi) and PdfBatchService
// (continue-on-error, skip-on-exists, force) are all exercised over fakes. The residual is the
// genuinely-untestable-in-isolation edge — thread-interruption handling in awaitAll and the
// best-effort temp-dir cleanup/createBeside fallbacks — which the E2E :app suites cover; chasing it
// here would mean mocking InterruptedException, not adding signal. PIT still lives on :domain (the
// pure-logic target); these services own thread pools and the filesystem.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.65".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
