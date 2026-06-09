import org.gradle.api.attributes.Attribute
import org.gradle.internal.os.OperatingSystem

// The Svelte SPA as a first-class Gradle build participant. This module owns NO Java; it drives the
// pnpm/Vite production build and republishes the built dist/ as a consumable artifact that
// :webapp:app embeds into the bootJar's static/ resources — so the SPA is built by the standard
// toolchain on every OS (the Docker-free CI app-image build, not only the runtime Dockerfile's node
// stage) and nothing is written into :webapp:app/src.
//
// It drives the PATH pnpm directly via Exec: the dev image bakes the pinned node/pnpm, and cross-OS CI
// adds them with setup-node + `npm i -g pnpm`. This matches the monorepo idiom (jlink/jpackage/ldd are
// all Exec/ExecOperations) and the "Gradle never auto-downloads a toolchain" rule. (An earlier cut used
// the node-gradle plugin, but its pnpmSetup step shells out to `npm` to install a SECOND pnpm, which
// fails EACCES in the dev container where HOME is not writable — and the system pnpm is the single
// source of truth anyway.)
plugins {
    base
}

// pnpm is `pnpm.cmd` on Windows (the npm-installed shim), `pnpm` elsewhere.
val pnpm = if (OperatingSystem.current().isWindows) "pnpm.cmd" else "pnpm"
val frontendDir = layout.projectDirectory

// Frozen, reproducible install — never mutate pnpm-lock.yaml (mirrors CI's `web` job + the Dockerfile).
val pnpmInstall =
    tasks.register<Exec>("pnpmInstall") {
        description = "pnpm install --frozen-lockfile (the PATH pnpm; never mutates the lockfile)"
        workingDir = frontendDir.asFile
        commandLine(pnpm, "install", "--frozen-lockfile")
        inputs.files(
            frontendDir.file("package.json"),
            frontendDir.file("pnpm-lock.yaml"),
            frontendDir.file("pnpm-workspace.yaml"),
        )
        outputs.dir(frontendDir.dir("node_modules"))
    }

// `pnpm build` = svelte-check (strict) + vite build -> dist/. Declared inputs/outputs give a correct
// up-to-date check; dist/ is Vite's conventional, gitignored output (OUT of src/, so no smell).
val buildSpa =
    tasks.register<Exec>("buildSpa") {
        group = "build"
        description = "svelte-check + vite build the SPA into dist/"
        dependsOn(pnpmInstall)
        workingDir = frontendDir.asFile
        commandLine(pnpm, "run", "build")
        inputs.dir(frontendDir.dir("src"))
        inputs.files(
            frontendDir.file("package.json"),
            frontendDir.file("pnpm-lock.yaml"),
            frontendDir.file("vite.config.ts"),
            frontendDir.file("svelte.config.js"),
            frontendDir.file("tsconfig.json"),
            frontendDir.file("index.html"),
        )
        outputs.dir(frontendDir.dir("dist"))
    }

// Producer: expose the built dist/ as a consumable artifact keyed by a custom attribute, so
// :webapp:app resolves exactly this module's SPA bundle (CC-safe; Gradle orders buildSpa before
// :webapp:app:processResources through the normal task graph, no cross-project .dependsOn).
val spaDist =
    configurations.consumable("spaDist") {
        attributes { attribute(Attribute.of("io.github.p4suta.artifact", String::class.java), "spa-dist") }
    }
artifacts {
    add(spaDist.name, frontendDir.dir("dist")) { builtBy(buildSpa) }
}
