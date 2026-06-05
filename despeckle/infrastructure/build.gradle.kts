import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    `java-test-fixtures`
}

// Adapters: FFM/Leptonica, PDFBox, AWT, and the jbig2/qpdf/cwebp exec wrappers all live here.
dependencies {
    implementation(project(":despeckle:domain"))
    implementation(project(":despeckle:port"))
    implementation(project(":shared:imaging"))
    // The cross-app external-process plumbing: ToolPath resolves the pdfimages/pdfinfo/jbig2/qpdf
    // binaries (via despeckle's own -Ddespeckle.<tool>.path keys), and ProcessRunner drives the
    // qpdf --linearize pass with its exit-3 tolerance. The thin NativeTools wrapper keeps the
    // binary capture / DespeckleException-tagged await semantics that shared:process cannot model.
    implementation(project(":shared:process"))
    // The cross-app PDF adapters donated from this app: the thin infrastructure.pdf wrappers
    // (PdfImagesCliExtractor, PdfBoxJbig2Assembler, QpdfLinearizer) bind these neutral capabilities
    // to despeckle's :port interfaces, fixing despeckle's own -Ddespeckle.<tool>.path keys. PDFBox
    // (and the xmpbox XMP packet) now live behind :shared:pdf, so they are no longer a direct
    // dependency of this module's main source.
    implementation(project(":shared:pdf"))
    // Charts.median delegates to the shared upper-median primitive, and LeptonicaPageCleaner maps
    // Leptonica's raw DPI to kernel Resolution at the FFM boundary — declared directly here rather
    // than leaning on :despeckle:domain re-exporting the kernel transitively.
    implementation(project(":shared:kernel"))
    // PDFBox and its xmpbox sibling are no longer a DIRECT dependency of this module's main source:
    // the only main user was PdfBoxJbig2Assembler, now a thin wrapper that delegates the PDFBox
    // container + XMP packet handling to :shared:pdf. They remain on the test-fixtures classpath
    // below (TestPdfs builds/inspects real PDFs with PDFBox directly).
    implementation(libs.slf4j.api)

    // TestImages + the PDFBox-backed PDF builder are shared with :app's cross-module tests.
    testFixturesImplementation(libs.pdfbox)
    testFixturesImplementation(libs.jspecify)
}

// Adapters that shell out to external binaries (jbig2/qpdf/cwebp/img2webp/pdfimages) or cross the
// FFM/process boundary: their defensive branches (native tool resolution, ProcessBuilder timeouts,
// FFM downcalls) cannot be unit-tested without unnatural scaffolding. They ARE exercised end-to-end
// by :app's pipeline tests — see the true numbers via `just coverage` (the aggregated report). They
// are excluded only from THIS module's isolated floor, so it tracks the genuinely unit-testable
// adapter logic (Leptonica page cleaner, the report renderer + charts).
val coverageExcludes =
    listOf(
        // The FFM Leptonica island moved to :shared:imaging; despeckle no longer has its own
        // Leptonica.class to exclude here. What stays excluded is the still-unit-untestable
        // shell-out / FFM-adjacent adapter surface below.
        "**/NativeTools.class",
        "**/QpdfLinearizer.class",
        "**/PdfBoxJbig2Assembler*.class",
        "**/PdfImagesCliExtractor.class",
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
