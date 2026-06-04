// build-logic is an included build holding the p4suta.* convention plugins. It re-imports the root
// version catalog so plugin-marker versions (and, transitively, the convention plugins) stay
// sourced from the one gradle/libs.versions.toml — the catalog remains the single source of truth.
//
// Precompiled script plugins get no generated type-safe `libs.` accessors, so the conventions read
// versions/libraries through VersionCatalogsExtension; this `create("libs")` is what makes that
// catalog visible inside this included build.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
