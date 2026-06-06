import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The web feature's pure core: the Job lifecycle state machine (Job + JobState), the validated
// conversion options (ConversionRequest with its Direction/FirstPage enums), the opaque JobId
// identity, and the typed not-found error. Framework-free and I/O-free — no Spring, no
// java.nio.file, no pipeline/tate types — so the web layer's "brains" are unit-tested in isolation
// and held to a domain-grade coverage floor. Leans on :shared:kernel for the precondition checks
// only. Gradle maps :webapp:domain -> webapp/domain/ automatically.
dependencies {
    implementation(project(":shared:kernel"))
}

// Domain-grade coverage floor (the same 0.95 line / 0.90 branch the other apps' :domain use): this
// is pure branch-rich logic with every arm reachable from a unit test.
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
