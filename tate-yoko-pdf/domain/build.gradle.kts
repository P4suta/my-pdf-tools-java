import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    // Adds the `api` configuration (purely additive over the conventions' `java` plugin) so the
    // exported error-kernel supertypes flow transitively to the layers that throw SpreadException.
    `java-library`
    alias(libs.plugins.pitest)
}

// The pure core: no third-party runtime libraries. Its one project dependency is the cross-app
// :shared:kernel error model — ErrorKind now implements ErrorCategory and SpreadException extends
// BaseAppException, both of which are public API. `api` (enabled by the java-library plugin above)
// so the kernel supertypes flow transitively to the application/infrastructure/app layers that
// throw SpreadException — without it, javac on those layers cannot walk SpreadException's supertype
// chain to Throwable.
dependencies {
    api(project(":shared:kernel"))
}

// Mutation testing (warning-only thresholds today; read the kill rate, then tighten).
pitest {
    pitestVersion = "1.20.2"
    junit5PluginVersion = "1.2.3"
    targetClasses = listOf("io.github.p4suta.tateyokopdf.domain.*")
    testStrengthThreshold = 0
    mutationThreshold = 0
    coverageThreshold = 0
    failWhenNoMutations = false
    timestampedReports = false
    outputFormats = listOf("HTML", "XML")
    jvmArgs =
        listOf(
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-Xshare:off",
        )
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
