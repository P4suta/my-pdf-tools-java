import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
}

// The first shared module: the cross-app kernel. Pure framework-free Java with NO runtime
// dependency on any other project module and NO third-party runtime library — only the conventions'
// compileOnly jspecify annotations. It holds the small primitives that all three apps (register,
// despeckle, tate-yoko-pdf) duplicate today: the dpi<->px/mm conversion (Resolution) and the
// exception-neutral precondition checks (Validators). App layers depend on it; it depends on
// nothing.
//
// It applies java-, test-, and quality-conventions (Spotless + SpotBugs) so the shared error model
// is held to the same formatting/bytecode-analysis gate as app code — quality-conventions was
// omitted from the original template and is retrofitted here. Mutation testing (pitest) stays an
// app-domain concern, layered on per app, so the shared kernel keeps a clean, copyable baseline.

// Coverage floor for the kernel. This is pure logic (a value type plus static checks), so it gets a
// domain-like floor — the same 0.95 line / 0.90 branch the despeckle :domain uses, not the laxer
// FFM-tolerant register floor. The private no-instance constructor and record accessors are covered
// reflectively / via EqualsVerifier in the tests so the floor is met honestly.
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
