# Distribution & packaging

How the four CLI tools (`register` / `despeckle` / `tate-yoko-pdf` / `pdfbook`)
and the `webapp` server are built, bundled, and shipped across Linux, macOS, and
Windows. This is the single place the build models, the per-OS prerequisites, the
local build recipes, and the runtime property scheme are written down; the
README files link here rather than repeat it.

The mechanics live in the `p4suta.distribution-conventions` build-logic plugin
(`selfContainedApp { }` DSL) and are continuously proven by
[`.github/workflows/distribution.yml`](../.github/workflows/distribution.yml),
which is the authoritative, CI-green record of exactly how each OS is
provisioned. When a local step here ever drifts from that workflow, the workflow
wins.

## Build models

The project ships in **complementary** ways — none replaces another:

| Model | Command | What it is | Applies to |
| --- | --- | --- | --- |
| Self-contained app-image | `just dist-package <app>` | A Docker-free, JDK-free [jpackage](https://docs.oracle.com/en/java/javase/25/jpackage/) app-image: a jlink-trimmed JRE plus the app's native closure, in one folder you unzip and run. Cross-OS (Linux / macOS / Windows). | all four CLI tools + `webapp` |
| Runtime Docker image | `just web-image` | A long-running-server image (JRE + `bootJar` + the dev image's native toolbox + the SPA). The **default recommendation for a resident server**. | `webapp` |
| installDist launcher | `just pdfbook-install` | A plain `application`-plugin launcher that resolves its natives from the host (the dev image on Linux). The inner-loop dev artifact. | CLI tools on Linux |

The two `webapp` models are intentionally parallel: the Docker image stays the
first-class way to run a resident LAN server (ADR
[0009](adr/0009-spring-boot-web-layer-and-deployment.md), 決定 6 and the 2026-06-09
revision); the app-image is the non-default, "drop a folder and run" option for
portfolio / local use. The `webapp` app-image links no natives itself — it
**nests the proven `pdfbook` app-image** under `$APPDIR/tools/pdfbook/` and points
the server at it via `-Dp4suta.pdfbook.path` (see the property scheme below).

**Public (WWW) exposure** is a property of the resident Docker model only, behind a
reverse proxy that terminates TLS and adds authentication / rate limiting — see
[`deploy/reverse-proxy/`](../deploy/reverse-proxy/) and the ADR-0009 2026-06-09
hardening revision. The self-contained app-image runs the default loopback profile
and has no proxy, so it is **LAN/local desktop only, not a public-exposure target**.

## Per-app native requirements

What each app bundles into its self-contained app-image:

| App | Leptonica (FFM) | pdfimages / pdfinfo | jbig2enc | qpdf | img2webp |
| --- | :-: | :-: | :-: | :-: | :-: |
| `register` | ✓ | ✓ | ✓ | – | – |
| `despeckle` | ✓ | ✓ | ✓ | ✓ | ✓ (optional) |
| `pdfbook` (`:pipeline`) | ✓ | ✓ | – | ✓ | – |
| `tate-yoko-pdf` | – | – | – | ✓ | – |
| `webapp` | – (nests `pdfbook`) | | | | |

Notes that drive the per-OS provisioning:

- **`pdfbook` does not use jbig2enc** — its register stage writes TIFF-G4 and the
  pack embeds CCITT G4, so the Windows pdfbook leg needs only Leptonica + poppler
  + qpdf. `register` / `despeckle` *do* need jbig2enc.
- **qpdf is per-OS**: on Linux/Windows it comes from an upstream release zip the
  Gradle build self-downloads (Ivy); on macOS there is no upstream Darwin release,
  so it is taken from Homebrew as a host tool.
- **jbig2enc is not packaged for Windows/macOS** — the CI legs for `register` /
  `despeckle` build it from source (pinned 0.31) against the toolchain Leptonica.

## OS prerequisites for a local app-image build

On **Linux** the dev container carries the whole toolchain, so nothing extra is
needed and no native prefix is passed. On **macOS / Windows** you build on the
host (jpackage emits an OS-native launcher, which a Linux container cannot
produce), so the host needs the native libraries and tools, and the build is
told where to find them with `-Pp4suta.nativePrefix`.

| OS | Provision | `-Pp4suta.nativePrefix` |
| --- | --- | --- |
| Linux | dev container (`Dockerfile`) — Leptonica, poppler, qpdf, jbig2enc, fonts | (none) |
| macOS | `brew install leptonica poppler qpdf` (+ for `register`/`despeckle`: build jbig2enc 0.31 from source, plus `autoconf automake libtool webp pkg-config`) | `$(brew --prefix)` |
| Windows | MSYS2 mingw64 `pacman -S mingw-w64-x86_64-{leptonica,poppler,binutils}` (+ for `register`/`despeckle`: the build toolchain, then jbig2enc 0.31 from source) | `C:\msys64\mingw64` |

The exact, CI-proven provisioning commands (pacman retry loop, Homebrew install,
the jbig2enc source build) live in
[`distribution.yml`](../.github/workflows/distribution.yml) — copy them from
there rather than re-deriving them.

## Local build recipes

`just dist-package <app>` builds an app-image for the current OS, picking the
right path automatically: on Linux it runs in the dev container with no prefix;
on macOS / Windows it runs the host Gradle with the OS-appropriate
`-Pp4suta.nativePrefix` (assuming the prerequisites above are installed).

```sh
just dist-package pdfbook     # pipeline/app/build/dist-jpackage/  (or dist-app/ when nesting)
just dist-package webapp      # webapp/app/build/dist-app/pdfbook-web/  (nests pdfbook)
just dist-package register
just dist-package despeckle
just dist-package tate
```

`just web-package` / `just web-app-run` remain as `webapp`-specific aliases
(build, and build-then-run the server on Linux). The cross-OS legs are verified
in CI; locally only the Linux path is runnable end-to-end.

## Runtime property scheme

Three distinct `-D…` / `-P…` knobs, easy to confuse:

- **`-Pp4suta.nativePrefix=<dir>`** — a **build-time** Gradle property. It tells
  the *packaging* step where the host's native libraries/tools live
  (`$(brew --prefix)` / `C:\msys64\mingw64`). It does not exist at runtime.
- **`-Dp4suta.<tool>.path=<path>`** — the **runtime** canonical override the
  self-contained convention bakes into every launcher (e.g.
  `-Dp4suta.pdfimages.path=$APPDIR/natives/pdfimages`,
  `-Dp4suta.pdfbook.path=$APPDIR/tools/pdfbook/…`). One uniform scheme so a bundle
  resolves its binaries regardless of which app launched. Going forward this is
  the override to set by hand, too.
- **`-D<app>.<tool>.path=<path>`** — the **legacy per-app** override
  (`register.leptonica.path`, `despeckle.qpdf.path`, `tateyokopdf.qpdf.path`, …).
  Still fully supported; predates the canonical scheme.

The two resolution mechanisms consult these keys in **opposite order** — do not
assume a single "canonical wins" rule:

- **External tools** — `ToolPath.resolve(tool, propertyKey)`
  ([`shared/process/.../ToolPath.java`](../shared/process/src/main/java/io/github/p4suta/shared/process/ToolPath.java))
  tries the **per-app key first**, then the canonical `p4suta.<tool>.path`, then
  the first match on `PATH`. So any caller (or test) that sets a per-app key wins.
- **Leptonica (FFM library load)** —
  ([`shared/imaging/.../Leptonica.java`](../shared/imaging/src/main/java/io/github/p4suta/shared/imaging/Leptonica.java))
  tries the **canonical `p4suta.leptonica.path` first**, then the per-app
  `register.leptonica.path` / `despeckle.leptonica.path`, then the standard
  multiarch locations.

In day-to-day use you rarely set any of these — the app-image launchers carry the
canonical keys, and on Linux the dev image's standard locations resolve
everything. Reach for an override only to point at a non-standard install.
