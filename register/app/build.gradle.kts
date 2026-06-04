import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

plugins {
    id("p4suta.java-conventions")
    id("p4suta.native-conventions")
    id("p4suta.test-conventions")
    id("p4suta.quality-conventions")
    application
    alias(libs.plugins.shadow)
}

// The composition root and runnable artifact: Main installs the fatal handler and delegates to the
// CLI, which wires the infrastructure adapters into the application services (the one module that
// sees both :application and :infrastructure). It owns the Apache Commons CLI front end and the
// self-contained, Docker-free jpackage app-image distribution (jlink + shadow + staged natives).
dependencies {
    // The composition root assembles the whole hexagon. Declaring each module directly (rather than
    // leaning on transitive edges) also anchors the ArchUnit layer definitions in this module's
    // test, so a future change to a module's dependencies can never make a layer go silently empty.
    implementation(project(":register:application"))
    implementation(project(":register:infrastructure"))
    implementation(project(":shared:observability"))
    implementation(project(":register:port"))
    implementation(project(":register:domain"))
    // The cross-app CLI scaffolding: the shared int/double/enum/positional/help/usage-error parsers
    // (CliOptionSupport) and the Throwable -> Error[KIND] + sysexits reporter (CliExceptionHandler)
    // the two front ends route through.
    implementation(project(":shared:cli"))
    // The CLI front end (the one layer allowed to write to System.out/err) lives here.
    implementation(libs.commons.cli)
    // The CLI logs through the SLF4J facade; the binding (slf4j-simple) is added on the runnable
    // app's runtime only (and the test runtime, via test-conventions). The library modules compile
    // against the facade alone.
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    // TestImages (infrastructure's published fixture) builds the synthetic pages the E2E tests drive.
    testImplementation(testFixtures(project(":register:infrastructure")))
    // The E2E tests read the registered output images back through the shared Pix handle (read /
    // width / height / resolution / writePng). :infrastructure depends on :shared:imaging only as an
    // `implementation` edge, which is not transitive on this module's test compile classpath, so the
    // test sources need their own (test-only) edge to the imaging island.
    testImplementation(project(":shared:imaging"))
    // The PDF pipeline E2E tests synthesize a scan PDF and read the registered output back with
    // PDFBox; it is a test-only dependency here (production PDFBox use is confined to :infrastructure).
    testImplementation(libs.pdfbox)
}

// The one place native access is granted to the launched app; run, test and JavaExec inherit it.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

application {
    mainClass = "io.github.p4suta.register.Main"
    // Keep the installDist launcher named `register` (matching the jpackage `--name register`);
    // otherwise the application plugin defaults the launcher name to the subproject name, `app`.
    applicationName = "register"
    applicationDefaultJvmArgs = nativeAccessArgs
}

// ---- Distribution: jlink + jpackage app-image (`just package`) --------------
// Produces build/dist-jpackage/register/ — a self-contained PDF -> PDF tool that needs neither
// Docker, a JDK, nor poppler/leptonica on the host. It bundles a trimmed JRE (jlink), the shaded jar
// (shadow), and the native artifacts the pipeline reaches for: the pdfimages/pdfinfo/jbig2 binaries
// plus the Leptonica .so that FFM loads, together with their transitive .so closure. jlink and
// jpackage are invoked directly (Exec on the toolchain's own tools) rather than via a plugin, to
// stay Gradle-9 configuration-cache clean.

tasks.shadowJar {
    // register-all.jar (no version in the name) is the stable --main-jar handle the jpackage
    // launcher references; mergeServiceFiles keeps the PDFBox/ImageIO SPI and slf4j ServiceLoader
    // registrations that a naive shade would clobber.
    archiveBaseName = "register"
    archiveClassifier = "all"
    archiveVersion = ""
    mergeServiceFiles()
}

// jlink/jpackage live next to the JVM running Gradle (Liberica Full in the dev image, which ships
// the jmods they consume). Resolve them off java.home.
val javaHomeProvider: Provider<String> =
    providers.systemProperty("java.home").orElse(providers.environmentVariable("JAVA_HOME"))

fun toolPath(tool: String): Provider<String> = javaHomeProvider.map { "$it/bin/$tool" }

// Pinned, not jdeps-derived: `jdeps --print-module-deps` is the floor we check against, but an
// explicit set keeps a dependency bump from silently shrinking the runtime. java.desktop is
// mandatory (PDFBox' image XObject path touches java.awt.image / ColorModel / ImageIO); java.xml
// backs xmpbox's XMP packet; jdk.zipfs / jdk.unsupported cover PDFBox stream filters + sun.misc.Unsafe
// in transitive deps. FFM itself is in java.base.
val bundledModules =
    listOf(
        "java.base",
        "java.desktop",
        "java.logging",
        "java.xml",
        "jdk.unsupported",
        "jdk.zipfs",
    )

// jlink and jpackage both refuse to write into an existing directory, while Gradle eagerly creates
// declared output dirs. So we declare the *parent* as the output and let the tool create a
// fixed-name child, with a Delete task resetting state between runs (configuration-cache safe,
// unlike project.delete in doFirst).
val jreOutputParent = layout.buildDirectory.dir("dist-jre")
val jreImageDir = jreOutputParent.map { it.dir("runtime") }
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")
val jpackageOutputParent = layout.buildDirectory.dir("dist-jpackage")

val cleanJreImage = tasks.register<Delete>("cleanJreImage") { delete(jreImageDir) }

val jreImage =
    tasks.register<Exec>("jreImage") {
        group = "distribution"
        description = "jlink a trimmed JRE into build/dist-jre/runtime"
        dependsOn(cleanJreImage)
        commandLine(
            toolPath("jlink").get(),
            "--add-modules",
            bundledModules.joinToString(","),
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=zip-9",
            "--output",
            jreImageDir.get().asFile.absolutePath,
        )
        inputs.property("modules", bundledModules.joinToString(","))
        outputs.dir(jreOutputParent)
    }

// Collect the four native seeds + their transitive .so closure into a single directory, each ELF
// re-pathed to RUNPATH=$ORIGIN so co-located siblings resolve without LD_LIBRARY_PATH (the jpackage
// launcher sets no environment). The glibc / dynamic-loader core stays host-resolved: bundling a
// build-image ld-linux/libc next to host-loaded objects yields two libcs in one process. The
// contract is therefore "host glibc >= build-image glibc" (noble, 2.39).
abstract class StageNatives
    @Inject
    constructor(
        private val execOps: ExecOperations,
        private val fsOps: FileSystemOperations,
    ) : DefaultTask() {
        @get:Input abstract val seeds: ListProperty<String>

        @get:Input abstract val excludePrefixes: ListProperty<String>

        @get:OutputDirectory abstract val outDir: DirectoryProperty

        @TaskAction
        fun stage() {
            val out = outDir.get().asFile
            fsOps.delete { delete(out) }
            out.mkdirs()
            val excludes = excludePrefixes.get()
            // Keyed by the SONAME the loader requests (the "name => /path" left side, == basename of
            // the path for these libs), so symlink aliases collapse and each object lands under the
            // name its dependents expect.
            val byName = linkedMapOf<String, File>()
            seeds.get().forEach { seed ->
                val seedFile = File(seed)
                require(seedFile.exists()) { "stageNatives: seed not found: $seed" }
                byName.putIfAbsent(seedFile.name, seedFile)
                val out2 = ByteArrayOutputStream()
                execOps.exec {
                    commandLine("ldd", seed)
                    standardOutput = out2
                }
                out2.toString().lineSequence().forEach { line ->
                    require(!line.contains("not found")) {
                        "stageNatives: unresolved dependency for $seed -> ${line.trim()}"
                    }
                    val dep =
                        Regex("""=>\s+(/\S+)""").find(line)?.let { File(it.groupValues[1]) }
                            ?: return@forEach
                    if (excludes.none { dep.name.startsWith(it) }) {
                        byName.putIfAbsent(dep.name, dep)
                    }
                }
            }
            byName.values.forEach { src ->
                // copyTo reads through symlinks, so the dst is a regular file under the requested
                // SONAME holding the real library bytes. It does NOT carry POSIX mode, so re-grant the
                // executable bit the binaries (pdfimages/pdfinfo/jbig2) need — otherwise register's
                // ProcessBuilder hits "Permission denied" launching them. Libraries stay non-exec.
                val dst = File(out, src.name)
                src.copyTo(dst, overwrite = true)
                dst.setWritable(true)
                if (src.canExecute()) {
                    dst.setExecutable(true, false)
                }
                execOps.exec { commandLine("patchelf", "--set-rpath", "\$ORIGIN", dst.absolutePath) }
            }
        }
    }

val stageNatives =
    tasks.register<StageNatives>("stageNatives") {
        group = "distribution"
        description = "Stage the pipeline's native binaries + .so closure into build/jpackage-natives"
        seeds.set(
            listOf(
                "/usr/lib/x86_64-linux-gnu/liblept.so.5",
                "/usr/bin/pdfimages",
                "/usr/bin/pdfinfo",
                "/usr/local/bin/jbig2",
            ),
        )
        excludePrefixes.set(
            listOf(
                "libc.so",
                "libm.so",
                "libpthread.so",
                "libdl.so",
                "librt.so",
                "libresolv.so",
                "libgcc_s.so",
                "ld-linux",
                "linux-vdso",
            ),
        )
        outDir.set(layout.buildDirectory.dir("jpackage-natives"))
    }

// jpackage's --input copies everything here into the app-image's app dir (the launcher's $APPDIR).
// So the jar lands at $APPDIR/register-all.jar and the natives under $APPDIR/natives/ — exactly the
// paths the launcher's -D options below point at.
val stageJpackageInput =
    tasks.register<Sync>("stageJpackageInput") {
        group = "distribution"
        description = "Stage the shaded jar + natives into build/jpackage-input for jpackage --input"
        from(tasks.shadowJar.flatMap { it.archiveFile })
        from(stageNatives.flatMap { it.outDir }) { into("natives") }
        into(jpackageInputDir)
    }

val cleanJpackageImage =
    tasks.register<Delete>("cleanJpackageImage") {
        delete(jpackageOutputParent.map { it.dir("register") })
    }

val jpackageImage =
    tasks.register<Exec>("jpackageImage") {
        group = "distribution"
        description = "jpackage the self-contained app-image into build/dist-jpackage/register"
        dependsOn(jreImage, stageJpackageInput, cleanJpackageImage)
        // \$APPDIR reaches jpackage (and the launcher .cfg) as the literal token $APPDIR; the launcher
        // expands it at run time to the app dir. Exec uses no shell, so nothing expands it at build
        // time. The Kotlin backslash only stops Kotlin string interpolation.
        commandLine(
            toolPath("jpackage").get(),
            "--type",
            "app-image",
            "--name",
            "register",
            "--input",
            jpackageInputDir.get().asFile.absolutePath,
            "--main-jar",
            "register-all.jar",
            "--main-class",
            application.mainClass.get(),
            "--runtime-image",
            jreImageDir.get().asFile.absolutePath,
            "--dest",
            jpackageOutputParent.get().asFile.absolutePath,
            "--app-version",
            project.version.toString(),
            "--java-options",
            "--enable-native-access=ALL-UNNAMED",
            "--java-options",
            "-Dregister.leptonica.path=\$APPDIR/natives/liblept.so.5",
            "--java-options",
            "-Dregister.pdfimages.path=\$APPDIR/natives/pdfimages",
            "--java-options",
            "-Dregister.pdfinfo.path=\$APPDIR/natives/pdfinfo",
            "--java-options",
            "-Dregister.jbig2.path=\$APPDIR/natives/jbig2",
        )
        inputs.dir(jreImageDir)
        inputs.dir(jpackageInputDir)
        outputs.dir(jpackageOutputParent)
    }

tasks.register("package") {
    group = "distribution"
    description = "Build the self-contained jpackage app-image (build/dist-jpackage/register)"
    dependsOn(jpackageImage)
}
