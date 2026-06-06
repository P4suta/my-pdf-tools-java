import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The adapters that bind the pipeline ports to the existing apps' services, treating each app as a
// library: PdfExtractSource (shared:pdf pdfimages) is the Source; DespeckleStage (despeckle's
// DespeckleService) and RegisterStage (register's RegistrationService) are Stages over the shared
// image working-set; SpreadPackSink (tate's SpreadService + the image-dir DocumentFactory) is the
// Sink that composes the RTL spread as the only repack. The apps are not modified — this is the one
// place that depends across them. native-conventions: the integration test drives Leptonica FFM.
dependencies {
    implementation(project(":pipeline:domain"))
    implementation(project(":pipeline:port"))

    // Per-page progress: the stages bridge each app service's framework-free PageProgressListener
    // (:shared:kernel) onto a PageProcessed event (:shared:progress) so the pipeline reports
    // page-level progress, not just stage boundaries.
    implementation(project(":shared:kernel"))
    implementation(project(":shared:progress"))

    // Source: the shared pdfimages extractor (extract the scan's pages once).
    implementation(project(":shared:pdf"))

    // Despeckle stage: its application service + the Leptonica cleaner adapter + its option/format
    // value types.
    implementation(project(":despeckle:application"))
    implementation(project(":despeckle:infrastructure"))
    implementation(project(":despeckle:domain"))
    implementation(project(":despeckle:port"))

    // Register stage: its application service + the Leptonica registrar/diagnostics adapters + its
    // option/format value types.
    implementation(project(":register:application"))
    implementation(project(":register:infrastructure"))
    implementation(project(":register:domain"))
    implementation(project(":register:port"))

    // Spread sink: tate's spread service + the image-backed DocumentFactory adapter + its value types.
    implementation(project(":tate-yoko-pdf:application"))
    implementation(project(":tate-yoko-pdf:infrastructure"))
    implementation(project(":tate-yoko-pdf:domain"))
    implementation(project(":tate-yoko-pdf:port"))

    implementation(libs.slf4j.api)

    // The integration test reads the final spread PDF back to assert geometry.
    testImplementation(libs.pdfbox)
}

// The adapters are thin delegations to native-backed services (Leptonica FFM, pdfimages/jbig2,
// PDFBox), exercised end-to-end by the integration test that runs the full pipeline on a synthetic
// sample scan in the dev image. The floor tracks that real execution; it is set below the apps'
// isolated-unit floors because there is little branch logic here beyond the happy path (the
// services own the algorithms), and the few defensive arms — pool shutdown in finally, an empty
// extraction — are not all reachable from one happy-path integration run.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
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
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
