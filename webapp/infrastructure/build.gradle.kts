import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The web feature's driven adapters. SubprocessConversionEngine shells out to the packaged pdfbook
// binary (resolved as an external tool) and tails its --progress-file by byte offset to stream
// events — process isolation, so a native crash never takes the server down and :webapp keeps ZERO
// compile dependency on :pipeline:* / :tate-yoko-pdf:*. The rest are plain: InMemoryJobStore
// (concurrent), FilesystemWorkspace (per-job dirs), BoundedConversionExecutor (single worker, bounded
// queue), UuidJobIdGenerator. No Spring, no FFM. Depends on :webapp:domain/port, :shared:progress
// (the JSONL codec) and :shared:process (timeout-bounded process plumbing only — the engine keeps
// its own ProcessBuilder because it must tail while the child runs, which the completion-oriented
// ProcessRunner cannot do). Gradle maps :webapp:infrastructure -> webapp/infrastructure/.
dependencies {
    implementation(project(":webapp:domain"))
    implementation(project(":webapp:port"))
    implementation(project(":shared:progress"))
    implementation(libs.slf4j.api)
}

// Infra-grade floor: four adapters are fully unit-tested; SubprocessConversionEngine is exercised
// end-to-end against a fake pdfbook script (success / failure / timeout / unparsable line) in the
// dev image. The branch floor is set modestly because the interrupt arm cannot be provoked
// deterministically, the same way the other apps' infra floors absorb their untestable catch arms.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.55".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
