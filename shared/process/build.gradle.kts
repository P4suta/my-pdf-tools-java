import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The sixth cross-app shared module: the external-process plumbing island. It generalizes the
// best-of-breed bits the three apps each re-derived in their own infrastructure.process /
// infrastructure.qpdf packages:
//
//   * ToolPath — resolve an external tool to a Path: an explicit -D<property> override wins (how the
//     packaged app-image points at its bundled binaries), else the first executable of that name on
//     PATH. Returns Optional<Path> so the policy ("missing tool = fatal" vs "= optional skip") stays
//     with the caller (register's separation). The property KEY is a parameter, never a unified
//     literal — each app keeps its own register.jbig2.path / despeckle.qpdf.path so packaged
//     app-image runs keep resolving.
//   * ProcessRunner — run a command to completion under a timeout, returning a typed Result
//     (exitCode + stdout + stderr + elapsed Duration; tate's typed Result is the shape donor) and
//     throwing TimeoutException on timeout. The caller passes the set of acceptable non-zero exits
//     (generalizing despeckle's hardcoded qpdf-exit-3 tolerance) — a non-acceptable exit throws.
//   * Tasks — awaitAll: a parameterized parallel fan-out over a caller-owned pool (register's),
//     collecting results in submission order and aggregating the first failure as an IOException.
//
// No domain exceptions are reachable here (that is the point of generalizing): a launch failure or
// an unacceptable exit surfaces as a plain IOException, a timeout as a TimeoutException — so the
// only dependency is the SLF4J facade. Unlike :shared:imaging this is plain ProcessBuilder plumbing,
// not FFM, so it does NOT apply p4suta.native-conventions. Gradle maps :shared:process ->
// shared/process/ automatically.
dependencies {
    // The SLF4J facade only. None of the public signatures leak a third-party or sibling-module
    // type, so `implementation` (not `api` / `java-library`) is enough — consumers compile against
    // java.nio.file.Path / java.time.Duration / java.util.concurrent types and the JDK exceptions.
    implementation(libs.slf4j.api)
}

// Coverage floor: the same realistic infra-like 0.75 line / 0.60 branch the despeckle
// :infrastructure and :shared:imaging modules use — this is process/exec plumbing, not branch-rich
// domain logic, so the domain-grade 0.95/0.90 the kernel/observability use would be dishonest. The
// only branches a unit test cannot drive are the InterruptedException catches (you cannot
// deterministically interrupt the waiting thread mid-waitFor) and a stray launch-failure path; at
// CLASS granularity the imaging-style exclusion would throw away ProcessRunner's genuinely covered
// happy/timeout/exit paths too, so NO class is excluded — the lenient floor absorbs the few
// untestable catch arms. The same self-contained block the other shared modules carry, since the
// floor is not applied by any convention plugin.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
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
