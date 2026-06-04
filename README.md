# my-pdf-tools-java

A monorepo of three command-line tools for self-scanned (自炊) Japanese-book PDFs
and the bitonal page images inside them, built on a shared hexagonal core.

## Tools

| App | What it does |
| --- | --- |
| [`register`](register/) | Aligns bitonal scans of 縦書き novel pages onto a fixed paper-size canvas, removing per-scan jitter (size / position / rotation). |
| [`despeckle`](despeckle/) | Removes scanner pepper-noise from bitonal scans while protecting ruby (振り仮名), 句読点, and dakuten/handakuten. |
| [`tate-yoko-pdf`](tate-yoko-pdf/) | Converts 縦書き scanned PDFs into right-to-left (RTL) two-page spreads that read correctly in any PDF viewer. |

## Layout

Each app is a hexagonal stack of five modules — `domain`, `port`, `application`,
`infrastructure`, `app` (composition root + runnable artifact). Cross-cutting
code lives in seven shared modules:

```
shared/
  kernel         core value types and utilities
  observability  logging, error mapping, exit codes
  imaging        bitonal image primitives
  cli            Apache Commons CLI front-end support
  process        external-process execution
  pdf            PDFBox-based PDF helpers
  io             filesystem / stream helpers
```

`register` and `despeckle` reach Leptonica through the Java FFM API (Linux-only
here); `tate-yoko-pdf` has no native FFM dependency and packages cross-OS
(Linux / macOS / Windows) with a bundled qpdf.

## Build

The build is **Docker-only** — there is no host JVM. The self-contained dev
image (root `Dockerfile`) carries the entire toolchain (Liberica JDK 25 Full,
Leptonica, the PDF toolbox, fonts, and the linters).

```sh
docker compose run --rm dev ./gradlew build   # full quality gate, all 22 modules
docker compose run --rm dev just build        # same, via the justfile
docker compose run --rm dev just lint          # peripheral linters
```

The quality gate runs Error Prone + NullAway (`-Werror`), SpotBugs (max effort),
Spotless, the JUnit/jqwik/ArchUnit suites, and per-module JaCoCo coverage
verification.

## CI & artifacts

- **Actions** — `ci` (build + check, lint), `distribution` (per-OS jpackage /
  installDist + smoke), `docs`, `dev-image`, `freshness`.
- **Javadoc (Pages)** — https://p4suta.github.io/my-pdf-tools-java/
- **dev image (GHCR)** — `ghcr.io/p4suta/my-pdf-tools-java-dev:latest`

## License

Each app keeps its own license (see its directory): `register` and `despeckle`
are dual-licensed MIT OR Apache-2.0; `tate-yoko-pdf` is MIT.
