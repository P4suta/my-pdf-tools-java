import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    alias(libs.plugins.springBoot)
}

// The web feature's Spring Boot front end and composition root: the @RestController, the SSE
// publisher, the @Configuration that wires the framework-free use cases + adapters, the @Scheduled
// reaper, and application.yml. The ONE module that sees Spring — the brains and adapters below stay
// framework-free (an ArchUnit rule pins that). pdfbook is resolved as an external tool via
// :shared:process ToolPath, so this module (like all of :webapp) never compiles against
// :pipeline:* / :tate-yoko-pdf:*. No coverage floor (the :app convention); the controller/SSE logic
// is tested directly with Mockito (no spring-test, to keep the JUnit/Mockito/AssertJ versions the
// shared catalog pins rather than the Spring BOM's). Applies the Spring Boot Gradle plugin per-module
// (the same shape as shadow on the CLI apps); `bootRun`/`bootJar` replace `application`/`shadow`.
dependencies {
    // The Spring Boot dependency BOM (its own plugin version), so spring-boot-starter-* need no
    // explicit version. Constrained to the production classpath only.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(project(":webapp:domain"))
    implementation(project(":webapp:port"))
    implementation(project(":webapp:application"))
    implementation(project(":webapp:infrastructure"))
    // SSE payloads reuse the shared JSONL codec; ToolPath resolves the pdfbook binary.
    implementation(project(":shared:progress"))
    implementation(project(":shared:process"))
    implementation(libs.slf4j.api)

    implementation("org.springframework.boot:spring-boot-starter-web")
}

springBoot {
    mainClass = "io.github.p4suta.webapp.app.WebApplication"
}
