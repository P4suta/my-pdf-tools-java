import org.gradle.api.attributes.Attribute
import org.gradle.language.jvm.tasks.ProcessResources
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("p4suta.java-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    id("p4suta.distribution-conventions")
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

// ---- Embed the SPA bundle into the bootJar's static/ -------------------------------------------
// Resolve :webapp:frontend's built dist/ through a dedicated resolvable configuration (matching its
// consumable `spaDist` by attribute) and stage it under static/ via processResources — so the Spring
// Boot fat jar (hence the self-contained app-image's bootJar) serves the SPA from its own origin,
// with NOTHING written into src/main/resources. The directory flows through the build only; the
// artifact's builtBy gives processResources a correct dependency on the SPA build (CC-safe). This is
// a distribution/static-asset wiring, NOT a code edge — :webapp:app still compiles against zero of
// :webapp:frontend (which has no Java).
// One custom attribute distinguishes the project's distribution-time directory artifacts: the SPA
// bundle here, and pdfbook's self-contained image below (both shared cross-project as directories).
val artifactKind = Attribute.of("io.github.p4suta.artifact", String::class.java)
val spaDist = configurations.dependencyScope("spaDist")
val spaDistFiles =
    configurations.resolvable("spaDistFiles") {
        extendsFrom(spaDist.get())
        attributes { attribute(artifactKind, "spa-dist") }
    }
dependencies {
    add(spaDist.name, project(":webapp:frontend"))
}
tasks.named<ProcessResources>("processResources") {
    from(spaDistFiles.map { it.asFileTree }) { into("static") }
}

springBoot {
    mainClass = "io.github.p4suta.webapp.app.WebApplication"
    // Generate build-info.properties so /actuator/info reports the build coordinates.
    buildInfo()
}

// ---- Self-contained distribution (`just web-package`) ------------------------------------------
// build/dist-app/pdfbook-web/ — the web server as a Docker-free, JDK-free app-image, the same first-
// class distribution the four CLI apps ship (additive to the runtime Docker image, see ADR-0009). The
// server links NO natives of its own (it shells out to pdfbook), so this image bundles none: it NESTS
// the proven pdfbook app-image under $APPDIR/tools/pdfbook and points the server at it via the
// canonical -Dp4suta.pdfbook.path the convention bakes into the launcher. The jpackage main artifact
// is the Spring Boot fat bootJar (SPA already embedded under static/, above), launched via JarLauncher.

// Resolve pdfbook's self-contained image through a distribution-time configuration (its consumable
// `selfContainedImage` variant). This carries the build edge so :webapp:app:package transitively
// builds :pipeline:app's image, while pulling ZERO jars onto any classpath — it is a directory
// artifact only, so :webapp keeps its zero compile dependency on :pipeline (ArchUnit-pinned).
val pdfbookImageDeps = configurations.dependencyScope("pdfbookImageDeps")
val pdfbookImage =
    configurations.resolvable("pdfbookImage") {
        extendsFrom(pdfbookImageDeps.get())
        attributes { attribute(artifactKind, "self-contained-image") }
    }
dependencies {
    add(pdfbookImageDeps.name, project(":pipeline:app"))
}

selfContainedApp {
    appName = "pdfbook-web"
    // The Spring Boot fat jar runs through its own loader; its classes live under BOOT-INF, so the
    // jpackage main-class is the loader's JarLauncher, never the @SpringBootApplication class.
    mainClass = "org.springframework.boot.loader.launch.JarLauncher"
    mainJar = "webapp-app-${project.version}.jar"
    // A generous Spring-Boot-4-web jlink set — pdfbook carries its OWN runtime, so NO union with its
    // modules is needed. Pinned; the CI server smoke (boot + /actuator/health + a real conversion) is
    // the floor that catches a missing module (`jdeps` is the lower bound to check against).
    modules =
        listOf(
            "java.base",
            "java.desktop",
            "java.instrument",
            "java.logging",
            "java.management",
            "java.naming",
            "java.net.http",
            "java.prefs",
            "java.security.jgss",
            "java.sql",
            "java.xml",
            "jdk.crypto.ec",
            "jdk.jfr",
            "jdk.unsupported",
            "jdk.zipfs",
        )
    // The single jpackage --input jar is the executable bootJar (the SPA + all deps are inside it).
    appArtifacts.from(tasks.bootJar)
    appArtifacts.builtBy(tasks.named("bootJar"))
    // Nest the proven pdfbook app-image; pass the resolvable configuration itself (not a mapped path)
    // so its cross-project build edge propagates. The server resolves it at run time through the
    // canonical -Dp4suta.pdfbook.path key the convention points at $APPDIR/tools/pdfbook.
    bundledApp("pdfbook", pdfbookImage)
}
