import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    // Adds the `api` configuration (purely additive over the conventions' `java` plugin) so the
    // commons-cli / kernel / observability types that surface in the parse helpers' and exception
    // handler's PUBLIC signatures flow transitively to the consuming app `cli` packages — without
    // it a consumer cannot compile against CliOptionSupport (Options/CommandLine/ParseException) or
    // CliExceptionHandler (ErrorCategory).
    `java-library`
}

// The fifth shared module: the cross-app CLI scaffolding, generalized from tate-yoko-pdf's
// best-of-breed Apache Commons CLI front end. This is the APP-LAYER scaffolding — the ONE shared
// module deliberately allowed to touch System.out/System.err and the Apache Commons CLI types. The
// kernel and observability layers stay stream-free and framework-light; the app-neutral glue that
// every CLI front end re-derives (input/output resolution, the stdin/stdout temp-file bridge, the
// continue-on-error batch loop, the throwable -> exit-code handler, and the parse helpers) lives
// here so register, despeckle, and tate-yoko-pdf adopt one copy instead of three.
dependencies {
    // The Severity -> Level mapper, the PII path sanitizer, and the sysexits ExitCodes registry the
    // exception handler and batch driver report through. `api` because ExceptionMapper / ExitCodes
    // surface in the public signatures consumers compile against (the extra-rule overload, the
    // returned exit codes).
    api(project(":shared:observability"))
    // The shared error model (ErrorCategory / CommonErrorKind). ErrorCategory leaks into the
    // exception handler's public surface (the OOM-hint key, the extra-rule Function), so `api`.
    api(project(":shared:kernel"))
    // Apache Commons CLI: this is the one shared module allowed to depend on it. `api` because
    // Options / CommandLine / ParseException appear in CliOptionSupport's public signatures, so a
    // consumer cannot compile against the parse helpers without it on the compile classpath.
    api(libs.commons.cli)
    // The SLF4J facade: the batch driver and exception handler log at the mapped severity.
    implementation(libs.slf4j.api)
}

// Coverage floor: an app-like 0.85 line / 0.65 branch, NOT the domain-like 0.95/0.90 the kernel and
// observability use. This is CLI / stream-bridge code (temp-file plumbing, System.out/in/err
// wiring, a Commons CLI help renderer) whose every line is exercised but whose defensive branches —
// an IOException from createTempFile, the help-render fallback — cannot all be driven from a unit
// test. The same self-contained block the other shared modules carry, since the floor is not
// applied by any convention plugin.
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
                minimum = "0.65".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
