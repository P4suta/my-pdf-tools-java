import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The cross-app progress island: the SINGLE vocabulary of progress / lifecycle events a pdfbook
// conversion emits, plus the one-object-per-line (JSONL) wire codec that carries them across a
// process boundary. A pdfbook subprocess writes these events to its --progress-file; a front end
// (the web layer's file tail -> SSE) reads them back. Producer and consumer share exactly one shape
// so neither re-encodes the other's data.
//
// Pure, framework-free value types + a dependency-free codec — the same baseline as :shared:kernel,
// on which it leans only for the exception-neutral precondition checks (Validators). No third-party
// runtime library, no I/O: the file/stream plumbing lives with each caller (the CLI's file sink, the
// web layer's WatchService tail). Gradle maps :shared:progress -> shared/progress/ automatically.
dependencies {
    // Validators only; no public signature leaks a kernel type, so `implementation` is enough.
    implementation(project(":shared:kernel"))
}

// Coverage floor: pure logic (value types + a hand-rolled codec), so it gets the same domain-grade
// 0.95 line / 0.90 branch the :shared:kernel uses — every codec branch (including the malformed-line
// error arms) is reachable from a unit test, so the lenient infra floor would be dishonest here. The
// self-contained block every shared module carries, since no convention plugin applies the floor.
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
