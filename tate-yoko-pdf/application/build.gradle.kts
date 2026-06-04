import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    alias(libs.plugins.pitest)
    `java-test-fixtures`
}

dependencies {
    implementation(project(":tate-yoko-pdf:domain"))
    implementation(project(":tate-yoko-pdf:port"))
    // The library layer compiles against the SLF4J facade alone; the binding (slf4j-simple) is
    // supplied at runtime by :app and at test time by p4suta.test-conventions. Domain stays log-free.
    implementation(libs.slf4j.api)
}

pitest {
    pitestVersion = "1.20.2"
    junit5PluginVersion = "1.2.3"
    targetClasses = listOf("io.github.p4suta.tateyokopdf.application.*")
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
