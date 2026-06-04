import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    `java-test-fixtures`
}

// Adapters: the Foreign Function & Memory island (Leptonica + its RAII handle Pix), the pixel-pushing
// page registrar (deskew / detect / scale / place), the PDFBox + pdfimages / jbig2 PDF wrappers, the
// opt-in (--diag) diagnostics renderers, and the scratch-dir / native-tool-path helpers. The one
// module that depends on PDFBox and the one that crosses the FFM / process boundary. Implements the
// :port interfaces; depends on :domain + :port.
dependencies {
    implementation(project(":register:domain"))
    implementation(project(":register:port"))

    // The framework-free upper-median primitive (Medians) shared across the apps, used by the
    // diagnostics residual summary (Residuals.median).
    implementation(project(":shared:kernel"))

    // The cross-app FFM imaging island: the unified Leptonica binding and its RAII Pix handle,
    // exposing only PRIMITIVE pixel ops. The register-specific deskew POLICY (the confidence-gated
    // straightening) is layered on these primitives app-side in this module's registrar package.
    implementation(project(":shared:imaging"))

    // The cross-app external-process plumbing: ToolPath (tool resolution via -D<key> override else
    // PATH), ProcessRunner (run-under-timeout with captured output), and Tasks (the parallel
    // worker-pool fan-out). The per-tool override keys (register.pdfimages.path, register.pdfinfo.path,
    // register.jbig2.path, register.img2webp.path) stay register's, passed in as the propertyKey arg.
    implementation(project(":shared:process"))

    // The cross-app PDF I/O island: the PDFBox lossless-JBIG2 assembler, the pdfimages/pdfinfo
    // extractor, and the pure pdfinfo / pdfimages -list parsers. Register's thin infrastructure.pdf
    // adapters bind onto these, passing register's own -D<tool>.path override keys. PDFBox and the
    // jbig2/pdfimages process calls now live entirely on this island's side of the boundary.
    implementation(project(":shared:pdf"))

    implementation(libs.slf4j.api)

    // TestImages (the synthetic-PBM builder) builds the pages the cross-module tests drive; it is
    // shared with :app's end-to-end suites.
    testFixturesImplementation(libs.jspecify)

    // PDFBox is no longer a MAIN dependency — the adapters delegate to :shared:pdf, which keeps
    // PDFBox on its own (non-api) classpath. Only the SamplePdfGenerator test tool still drives
    // PDFBox directly to build the synthetic sample scan, so it stays a test-only dependency here.
    testImplementation(libs.pdfbox)
}

// Adapters that cross the FFM / process boundary (the Leptonica binding, the page registrar's pixel
// ops, the pdfimages / jbig2 exec wrappers, the img2webp flip-book): their defensive branches
// (native tool resolution, ProcessBuilder timeouts, FFM downcalls) cannot be unit-tested without
// unnatural scaffolding. They ARE exercised end-to-end by :app's pipeline tests — see the true
// numbers via `just coverage` (the aggregated report). They are excluded only from THIS module's
// isolated floor, which then tracks the genuinely unit-testable adapter logic (Pix, the column
// detector, the diagnostics renderers, the process/path helpers) at a measured ~92% line / 64%
// branch; calibrated below with margin.
val coverageExcludes =
    listOf(
        "**/leptonica/Leptonica.class",
        "**/registrar/LeptonicaPageRegistrar.class",
        "**/process/NativeTools.class",
        "**/pdf/PdfImagesCliExtractor.class",
        "**/pdf/PdfBoxJbig2Assembler*.class",
        "**/diag/WebpFlipbook.class",
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
                minimum = "0.88".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.58".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }

// Generate a synthetic, copyright-free bitonal sample PDF for the smoke test and manual runs (`just
// smoke`). The generator lives in test sources, so it never ships in the production jar; the project
// is therefore exercisable end-to-end with zero external (copyrighted) input.
tasks.register<JavaExec>("createSamplePdf") {
    group = "verification"
    description =
        "Generate a synthetic bitonal sample PDF at infrastructure/build/sample/sample.pdf"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.p4suta.register.infrastructure.tools.SamplePdfGenerator"
    args = listOf("build/sample/sample.pdf", "4")
}
