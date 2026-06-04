import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    `java-test-fixtures`
}

dependencies {
    implementation(project(":tate-yoko-pdf:domain"))
    implementation(project(":tate-yoko-pdf:port"))
    // The cross-app PDF I/O island. QpdfLinearizer is a thin wrapper over :shared:pdf's QpdfRunner
    // (QpdfRunner.modernizing builds the qpdf --linearize / --min-version / --newline-before-endstream
    // command and runs it with qpdf's exit-3 tolerance); this app layers its bundle-first binary
    // resolution and its PDF_WRITE_FAILED throw-on-failure policy over the runner's typed Result.
    implementation(project(":shared:pdf"))
    // The cross-app external-process plumbing. QpdfLinearizer still names :shared:process's
    // ProcessRunner.Result (the exit-code debug log) and SpreadException-wraps the runner's
    // IOException/TimeoutException/InterruptedException, so the direct dependency stays even though
    // :shared:pdf re-exports ProcessRunner.Result transitively (as `api`).
    implementation(project(":shared:process"))
    implementation("org.apache.pdfbox:pdfbox:3.0.7")
    // xmpbox ships with PDFBox and shares its version — it builds the pdfaid / Dublin Core / Adobe
    // PDF XMP packet required for PDF/A conformance.
    implementation("org.apache.pdfbox:xmpbox:3.0.7")
    // The library layer compiles against the SLF4J facade alone; the binding (slf4j-simple) is
    // supplied at runtime by :app and at test time by p4suta.test-conventions.
    implementation(libs.slf4j.api)

    // PdfFixtures builds real PDFs with PDFBox for tests across modules.
    testFixturesImplementation("org.apache.pdfbox:pdfbox:3.0.7")
    testFixturesImplementation("org.jspecify:jspecify:1.0.0")
}

// QpdfLinearizer is a thin out-of-process wrapper whose defensive branches (bundle resolution,
// the shared ProcessRunner timeout, thread interruption) cannot be unit-tested without unnatural
// scaffolding; SamplePdfGenerator is a dev tool. Both are excluded from the coverage floor (their
// public paths are still exercised — see QpdfLinearizerTest). The raw ProcessBuilder plumbing now
// lives in :shared:process and is covered by that module's own ProcessRunnerTest.
val coverageExcludes =
    listOf(
        "io/github/p4suta/tateyokopdf/infrastructure/pdfbox/tools/**",
        "io/github/p4suta/tateyokopdf/infrastructure/qpdf/QpdfLinearizer.class",
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
