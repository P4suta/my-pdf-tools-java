import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The second shared module: cross-app observability. The framework-touching layer the kernel
// deliberately excludes — it owns the single Severity -> slf4j Level translation, the throwable->kind
// fallback, the PII path sanitizer, the fatal uncaught-exception handler, and the sysexits ExitCodes
// registry. Generalized from tate-yoko-pdf's :observability so register and despeckle can adopt the
// same primitives instead of each re-deriving them.
dependencies {
    // The shared error model (ErrorCategory / Severity / CommonErrorKind / BaseAppException). The
    // mapper reads exitCode()/severity() off the ErrorCategory; the kernel itself stays slf4j-free.
    implementation(project(":shared:kernel"))
    // The SLF4J facade: this layer is where the Severity -> Level translation and the logging live.
    implementation(libs.slf4j.api)
}

// Coverage floor: the same domain-like 0.95 line / 0.90 branch the kernel uses, since this is pure
// mapping logic (a data-driven mapper, a regex sanitizer, a tiny handler, and constants). The
// private no-instance constructors are covered reflectively / by exercising every branch in the
// tests so the floor is met honestly.
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
