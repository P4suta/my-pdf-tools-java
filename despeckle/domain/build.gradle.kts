import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    alias(libs.plugins.pitest)
    // The domain's public exception API (DespeckleException.kind() -> ErrorCategory, the
    // DespeckleErrorKind enum) exposes :shared:kernel types, so the kernel must be exported (api)
    // rather than kept implementation-only — otherwise :application / :infrastructure / :app, which
    // throw and inspect DespeckleException, cannot see ErrorCategory / BaseAppException.
    `java-library`
}

// The pure core: depends only on the cross-app :shared:kernel primitives and no third-party runtime
// libraries. (jspecify is the conventions' compileOnly annotation dependency, not declared here.)

dependencies {
    // Exception-neutral precondition checks (Validators) shared across the apps; ProcessOptions
    // delegates its non-positive guards to them. Exported via `api` because DespeckleException /
    // DespeckleErrorKind surface kernel types (ErrorCategory, BaseAppException) in their public API.
    api(project(":shared:kernel"))
}

// Mutation testing (warning-only thresholds today; read the kill rate, then tighten).
pitest {
    pitestVersion = libs.versions.pitest.get()
    junit5PluginVersion = libs.versions.pitestJunit5Plugin.get()
    targetClasses = listOf("io.github.p4suta.despeckle.domain.*")
    failWhenNoMutations = false
    timestampedReports = false
    outputFormats = listOf("HTML", "XML")
    mutationThreshold = 0
    coverageThreshold = 0
}

// Domain is the most-tested layer: the strictest coverage floor.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
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
