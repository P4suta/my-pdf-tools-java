import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    // `java-library` so the shared error kernel can be exposed as `api`: the domain exception model
    // (RegisterException extends BaseAppException, RegisterErrorKind implements ErrorCategory) puts
    // kernel types on this module's public surface, which the application/infrastructure/app layers
    // then name (the CLI's mapper integration compares an ErrorCategory kind).
    `java-library`
    alias(libs.plugins.pitest)
}

// The pure registration kernel: framework-free Java that depends only on the cross-app
// :shared:kernel primitives (Resolution, Validators) and no third-party runtime library.
// Projection-profile reductions, the per-parity median reference, transform planning, and the value
// types (Box, Transform, Canvas, PaperSize, Anchor, Parity, OutputFormat, RegisterOptions, the
// diagnostic records). The Leptonica/Pix binding and the pixel-pushing renderer that drive these
// live in :infrastructure, behind the :port boundary, so this module stays pure and a future GUI
// could reuse it unchanged.

dependencies {
    // The framework-free dpi<->px/mm conversion (Resolution) and exception-neutral precondition
    // checks (Validators) shared across the apps, plus the error model the domain exception extends.
    // `api` (not `implementation`) because RegisterException extends BaseAppException and
    // RegisterErrorKind implements ErrorCategory — both public API — so the kernel types flow
    // transitively to the application/infrastructure/app layers that name them.
    api(project(":shared:kernel"))
}

// Mutation testing (warning-only thresholds today; read the kill rate, then tighten). The pure math
// here is exactly the target mutation testing is meaningful for.
pitest {
    pitestVersion =
        libs.versions.pitest
            .get()
    junit5PluginVersion =
        libs.versions.pitestJunit5Plugin
            .get()
    targetClasses = listOf("io.github.p4suta.register.domain.*")
    testStrengthThreshold = 0
    mutationThreshold = 0
    coverageThreshold = 0
    failWhenNoMutations = false
    timestampedReports = false
    outputFormats = listOf("HTML", "XML")
    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Xshare:off")
}

// Coverage floor for the kernel's own unit tests. Measured ~92% line / 94% branch (pure math: the
// FFM excised to :infrastructure, the validation/median/projection/transform logic unit- and
// property-tested), floored below with margin. The residual is honest and deliberate, not a testing
// gap: (1) the ~10 diagnostic/value records (Canvas, Skew, Detection, PageAnalysis, RunInfo,
// PageDiagnostic + Column/Placement) are populated by :infrastructure/:application and never
// instantiated by the domain's own logic, so the 0% there is correct — exercising them here would
// only re-test compiler-generated record accessors; (2) Reference's `kept.isEmpty()` arm is
// unreachable given outlierRatio ∈ (0, 1] (the max-area box always clears `area >= ratio*median`),
// so it is documented defensive code, not a missed test; (3) Parity.of is called from :application,
// not the domain kernel. The aggregated coverage report (`just coverage`) gives the project-wide
// picture that credits those records to the layers that actually build them.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
