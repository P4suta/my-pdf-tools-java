package io.github.p4suta.gradle.dist

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.internal.os.OperatingSystem

/**
 * Declares the per-host qpdf release binary as a resolvable Gradle dependency, fetched from qpdf's
 * GitHub releases via an Ivy URL repository, and returns the configuration to wire into
 * `selfContainedApp { qpdfZip.from(...) }`. One reusable place for the repository, the role-based
 * configuration, and the per-OS classifier so tate and pipeline cannot drift.
 *
 * macOS has no upstream Darwin binary, so the configuration is left empty there (qpdf resolution
 * falls through to PATH / no-op); a Homebrew-sourced macOS qpdf is wired through the toolchain-prefix
 * source instead, alongside the other macOS natives.
 *
 * @param version the qpdf release to fetch (e.g. `12.3.2`)
 * @return a resolvable configuration carrying the host's qpdf zip (empty on macOS)
 */
fun Project.qpdfBinary(version: String): Configuration {
    repositories.ivy {
        name = "qpdf-releases"
        setUrl("https://github.com/qpdf/qpdf/releases/download/")
        patternLayout { artifact("v[revision]/qpdf-[revision]-[classifier].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("com.github.qpdf") }
    }
    // Modern role separation: dependencies are declared on a dependency-scope configuration, and a
    // separate resolvable configuration extends it to fetch the artifact. (A single legacy
    // create { } that is both declarable and resolvable is deprecated.)
    val declarations = configurations.dependencyScope("qpdfDeps")
    val resolvable = configurations.resolvable("qpdfBinary") { extendsFrom(declarations.get()) }
    val classifier =
        when {
            OperatingSystem.current().isLinux -> "bin-linux-x86_64"
            OperatingSystem.current().isWindows -> "mingw64"
            else -> null
        }
    if (classifier != null) {
        dependencies.add(declarations.name, "com.github.qpdf:qpdf:$version:$classifier@zip")
    }
    return resolvable.get()
}
