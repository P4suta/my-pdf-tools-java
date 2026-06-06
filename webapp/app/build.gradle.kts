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
    // Bean Validation (Hibernate Validator) so @Validated @ConfigurationProperties fails fast on a
    // missing / non-positive value at startup.
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Actuator (brings Micrometer core) for health + metrics; the Prometheus registry adds the
    // /actuator/prometheus scrape endpoint. Both managed by the Spring Boot BOM (no explicit version).
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    // OpenAPI docs + Swagger UI (/v3/api-docs, /swagger-ui.html). Not BOM-managed, so versioned in
    // the catalog; the 3.0.x line targets Spring Boot 4 / Jackson 3.
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    // NB: spring-boot-configuration-processor (IDE metadata for app.*) is deliberately NOT added.
    // Under the module's `-Xlint:all -Werror`, an active annotation processor makes javac warn
    // "No processor claimed ..." for every Spring/Jakarta annotation, failing the build. The metadata
    // is a minor DX nicety and not worth carving a per-module lint exception into the uniform gate.

    // Spring's test slices (@WebMvcTest / MockMvcTester / @SpringBootTest) on the TEST classpath
    // only. The Boot BOM here governs spring-test/spring-boot-test; the JUnit/Mockito/AssertJ
    // versions stay on the shared catalog's pins (junit-bom 6.1.0 versions the JUnit family;
    // mockito-core/assertj-core are direct testImplementation deps in p4suta.test-conventions) —
    // highest-version-wins across the two non-strict BOMs favors the catalog. Boot 4 baselines
    // JUnit 6 + Mockito 5.20, so there is no split-family clash. Boot 4 modularized the test slices
    // out of spring-boot-starter-test, so @WebMvcTest needs the spring-boot-starter-webmvc-test
    // companion; spring-boot-starter-test still provides @SpringBootTest + the core test libs.
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

// This module brings Logback (via spring-boot-starter-logging) the way the bootJar runtime does, so
// the shared test convention's slf4j-simple would be a SECOND SLF4J binding — and Spring Boot's
// LogbackLoggingSystem fails fast when it sees Logback on the classpath but a non-Logback
// LoggerFactory. Drop slf4j-simple from the test runtime so Logback is the sole binding (and the
// Spring context — @SpringBootTest / @WebMvcTest — initializes its logging the same as in production).
configurations.testRuntimeClasspath {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

springBoot {
    mainClass = "io.github.p4suta.webapp.app.WebApplication"
    // Generate build-info.properties so /actuator/info reports the build coordinates.
    buildInfo()
}
