import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// Orchestration: PipelineRunner folds a Source -> Stages -> Sink over per-run temp subdirectories
// and removes them at the end, so the only durable artifacts are the source and the single output —
// no intermediate PDFs. Pure: it constructs no adapters and names no PDF/image engine, so it is
// driven by fakes in tests. slf4j-api is the only third-party dependency (the binding ships in :app).
dependencies {
    implementation(project(":pipeline:domain"))
    implementation(project(":pipeline:port"))
    // The shared progress/lifecycle event vocabulary the runner emits into its ProgressSink.
    implementation(project(":shared:progress"))
    // Resolves a throwable to its stable ErrorCategory kind so RunFailed carries the kind token
    // (e.g. OUTPUT_CONFLICT) front ends localize from, not a Java class name.
    implementation(project(":shared:observability"))
    // ExceptionMapper.Mapping.kind() returns the kernel's ErrorCategory, dereferenced for name().
    implementation(project(":shared:kernel"))
    implementation(libs.slf4j.api)
}

// Floor for the runner's isolated unit tests (fake Source/Stage/Sink). Branch floor is set below the
// other apps' 0.65 because the only branches here beyond the happy path are the best-effort cleanup
// catch arms (a delete that fails mid-walk), which cannot be provoked without an unnatural
// read-only-filesystem harness — exactly the kind of defensive code the other modules also exclude
// from their floors. Line coverage stays high.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
