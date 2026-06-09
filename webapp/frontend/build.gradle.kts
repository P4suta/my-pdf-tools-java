import com.github.gradle.node.pnpm.task.PnpmInstallTask
import com.github.gradle.node.pnpm.task.PnpmTask
import org.gradle.api.attributes.Attribute

// The Svelte SPA as a first-class Gradle build participant. This module owns NO Java; it drives the
// pnpm/Vite production build through the node-gradle plugin and republishes the built dist/ as a
// consumable artifact that :webapp:app embeds into the bootJar's static/ resources — so the SPA is
// built by the standard toolchain on every OS (the Docker-free CI app-image build, not only the
// runtime Dockerfile's node stage) and nothing is written into :webapp:app/src.
//
// Toolchain policy: node + pnpm are SYSTEM-provided (download = false), matching the monorepo rule
// that Gradle never auto-downloads a toolchain (every gradlew run passes
// -Dorg.gradle.java.installations.auto-download=false; the JDK comes from the dev image / setup-java).
// The single source of truth for the node/pnpm versions stays the Dockerfile ARGs that
// checkExtraVersions tracks; this module uses whatever node/pnpm are on PATH (the dev image bakes the
// pinned pair; cross-OS CI installs them with setup-node + `npm i -g pnpm`). The plugin installs pnpm
// via npm — never corepack — so the corepack signature trap never applies.
plugins {
    alias(libs.plugins.node)
}

node {
    download = false
    // Run pnpm in this dir, where package.json + pnpm-lock.yaml + pnpm-workspace.yaml (the
    // allowBuilds / confirmModulesPurge policy) live.
    nodeProjectDir = layout.projectDirectory
}

// Frozen, reproducible install — never mutate pnpm-lock.yaml in the build (mirrors CI / the Docker
// build / `just`). The plugin's pnpmInstall already tracks package.json + pnpm-lock.yaml -> node_modules.
tasks.named<PnpmInstallTask>("pnpmInstall") {
    args = listOf("--frozen-lockfile")
    // The supply-chain policy file (allowBuilds: esbuild) — re-install if it changes.
    inputs.file(layout.projectDirectory.file("pnpm-workspace.yaml"))
}

// `pnpm build` = svelte-check (strict) + vite build -> dist/. Declared inputs/outputs give Gradle a
// correct up-to-date check; dist/ is Vite's conventional, gitignored output (OUT of src/, so there is
// no build-output-in-source smell) and is exactly what :webapp:app embeds.
val buildSpa =
    tasks.register<PnpmTask>("buildSpa") {
        group = "build"
        description = "svelte-check + vite build the SPA into dist/"
        dependsOn(tasks.named("pnpmInstall"))
        pnpmCommand = listOf("run", "build")
        inputs.dir(layout.projectDirectory.dir("src"))
        inputs.files(
            layout.projectDirectory.file("package.json"),
            layout.projectDirectory.file("pnpm-lock.yaml"),
            layout.projectDirectory.file("vite.config.ts"),
            layout.projectDirectory.file("svelte.config.js"),
            layout.projectDirectory.file("tsconfig.json"),
            layout.projectDirectory.file("index.html"),
        )
        outputs.dir(layout.projectDirectory.dir("dist"))
    }

// Producer: expose the built dist/ as a consumable artifact keyed by a custom attribute, so
// :webapp:app resolves exactly this module's SPA bundle. CC-safe; Gradle orders buildSpa before
// :webapp:app:processResources through the normal task graph (no cross-project .dependsOn).
val spaDist =
    configurations.consumable("spaDist") {
        attributes { attribute(Attribute.of("io.github.p4suta.artifact", String::class.java), "spa-dist") }
    }
artifacts {
    add(spaDist.name, layout.projectDirectory.dir("dist")) { builtBy(buildSpa) }
}
