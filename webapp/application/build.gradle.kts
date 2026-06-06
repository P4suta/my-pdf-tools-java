import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The web feature's use cases — the "brains": Conversions (submit -> queue -> run -> terminal
// state, plus query and result lookup) orchestrating the driven ports, and JobReaper (TTL cleanup).
// Framework-free: no Spring, driven entirely through the :webapp:port interfaces, so every path is
// exercised with fakes (a synchronous executor runs the conversion task inline). Depends on
// :webapp:domain, :webapp:port and :shared:progress; slf4j for best-effort warnings. Gradle maps
// :webapp:application -> webapp/application/ automatically.
dependencies {
    implementation(project(":webapp:domain"))
    implementation(project(":webapp:port"))
    implementation(project(":shared:progress"))
    implementation(libs.slf4j.api)
}

// Application-grade floor: this is pure orchestration logic driven by fakes, so it is held high
// (0.90 line / 0.80 branch) — every success/failure/rollback arm is reachable in a unit test.
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
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
