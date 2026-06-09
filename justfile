# my-pdf-tools-java (monorepo root) — task entry points.
#
# Routes through the unified dev container unless INSIDE_CONTAINER=1. The dev image
# (./Dockerfile) sets INSIDE_CONTAINER=1, so the same recipes call ./gradlew
# directly inside the container instead of re-entering Docker. Host machines need
# nothing but Docker.
#
# The root is now a single Gradle composite build (settings.gradle.kts + ./gradlew):
# the four PDF features (register / despeckle / tate-yoko-pdf / pipeline), the web
# layer (:webapp), and the shared modules. Every recipe drives that one build —
# there are no per-app justfiles anymore.

inside := env_var_or_default("INSIDE_CONTAINER", "0")

dev_running := `docker compose ps --status running --services 2>/dev/null | grep -c '^dev$' 2>/dev/null || true`
docker_run := if dev_running == "0" { "docker compose run --rm dev" } else { "docker compose exec dev" }

gradlew := if inside == "1" { "./gradlew" } else { docker_run + " ./gradlew" }

# Shell prefix for tools that live in the SPA's pnpm toolchain (Prettier): run directly when already
# in the container, else enter the dev container.
sh_run := if inside == "1" { "sh -lc" } else { docker_run + " sh -lc" }

# Language-agnostic quality tools, routed through the same dev container as
# ./gradlew (the dev image bundles typos/taplo/biome/yamlfmt/actionlint/lefthook).
typos := if inside == "1" { "typos" } else { docker_run + " typos" }
taplo := if inside == "1" { "taplo" } else { docker_run + " taplo" }
biome := if inside == "1" { "biome" } else { docker_run + " biome" }
yamlfmt := if inside == "1" { "yamlfmt" } else { docker_run + " yamlfmt" }
actionlint := if inside == "1" { "actionlint" } else { docker_run + " actionlint" }
lefthook := if inside == "1" { "lefthook" } else { docker_run + " lefthook" }

# Gradle: no daemon (containers are ephemeral), plain console, and never
# auto-download a JDK — the image's Liberica 25 Full is the toolchain. Identical
# to the flags the per-app builds + CI run, so they cannot drift.
gradle_flags := "--no-daemon --console=plain -Dorg.gradle.java.installations.auto-download=false"

# Host port the web server publishes (override: `WEB_PORT=9090 just web-serve`).
web_port := env_var_or_default("WEB_PORT", "8080")

# Host port the SPA dev server (Vite) publishes (override: `UI_PORT=5174 just web-ui`).
ui_port := env_var_or_default("UI_PORT", "5173")

default:
    @just --list

# Full quality gate: compile, checks, and the test suites.
build:
    {{gradlew}} build {{gradle_flags}}

# Test suites only.
test:
    {{gradlew}} test {{gradle_flags}}

# Verification gate (check tasks: lint + tests, no build artifacts).
check:
    {{gradlew}} check {{gradle_flags}}

# Compile + assemble the distributions without running checks (fast inner loop).
assemble:
    {{gradlew}} assemble {{gradle_flags}}

# Delete all build outputs.
clean:
    {{gradlew}} clean {{gradle_flags}}

# Open an interactive shell in the dev container.
shell:
    {{docker_run}} bash

# Passthrough: run an arbitrary Gradle invocation in the dev container.
#   just gradle :pipeline:app:installDist
gradle *ARGS:
    {{gradlew}} {{ARGS}} {{gradle_flags}}

# ----- pdfbook CLI + local web app -----

# Build the pdfbook CLI launcher (pipeline/app/build/install/pdfbook/bin/pdfbook).
pdfbook-install:
    {{gradlew}} :pipeline:app:installDist {{gradle_flags}}

# Run the built pdfbook CLI in the dev container (needs the native toolchain).
#   just pdfbook scan.pdf -o scan_book.pdf
pdfbook *ARGS: pdfbook-install
    {{docker_run}} pipeline/app/build/install/pdfbook/bin/pdfbook {{ARGS}}

# Run the pdfbook web server (Spring Boot, :8080) — pair with `just web-ui`.
web-serve: pdfbook-install
    #!/usr/bin/env bash
    set -euo pipefail
    # Build the server jar first — a Gradle step that COMPLETES (and releases the Gradle-home lock) —
    # then run it as a plain `java -jar` process, NOT `gradlew bootRun`. A running `java` server holds
    # no Gradle lock, so `just build` and every other recipe keep working while it serves; the dev
    # loop is just `edit -> just build -> just web-serve` with nothing to stop first. Binds 0.0.0.0
    # for VSCode port-forwarding / the host IP; override the published host port with WEB_PORT.
    {{gradlew}} :webapp:app:bootJar {{gradle_flags}}
    run='jar=$(ls -t webapp/app/build/libs/*.jar | grep -v -- -plain | head -1); export PATH="$PWD/pipeline/app/build/install/pdfbook/bin:$PATH"; exec java -jar "$jar" --server.address=0.0.0.0'
    if [ "{{inside}}" = "1" ]; then
        exec bash -lc "$run"
    fi
    docker rm -f pdfbook-web >/dev/null 2>&1 || true
    exec docker compose run --rm --name pdfbook-web -p {{web_port}}:8080 dev bash -lc "$run"

# Stop the web server container started by `just web-serve`.
web-stop:
    #!/usr/bin/env bash
    set -uo pipefail
    # `docker rm -f` exits 0 even for a missing container, so check existence first
    # to report honestly.
    if [ -n "$(docker ps -aq --filter name=^pdfbook-web$)" ]; then
        docker rm -f pdfbook-web >/dev/null && echo "web server stopped"
    else
        echo "no web server running"
    fi

# Start the SPA's Vite dev server INSIDE the dev container (the dev image's pinned pnpm — no host Node
# needed; the version matches the frontend's packageManager field). Proxies /api to the :8080 server,
# so pair it with `just web-serve` in another terminal. Publishes Vite's port to the host.
web-ui:
    #!/usr/bin/env bash
    set -euo pipefail
    run='cd webapp/frontend && pnpm install && pnpm dev --host 0.0.0.0'
    if [ "{{inside}}" = "1" ]; then
        exec bash -lc "$run"
    fi
    exec docker compose run --rm -p {{ui_port}}:5173 dev bash -lc "$run"

# Build the runtime Docker image: Gradle builds the SPA into the bootJar (single JDK+node build
# stage), plus pdfbook and its native toolbox. Run it with `docker run --rm -p 127.0.0.1:8080:8080
# pdfbook-web`. This is the long-running-server distribution; `just web-package` builds the Docker-free
# app-image alternative (see ADR-0009).
web-image:
    docker build -f webapp/app/Dockerfile -t pdfbook-web .

# Build the self-contained webapp app-image in the dev container: the Spring Boot server as a
# Docker-free, JDK-free app-image with the pdfbook app-image NESTED inside it (and the SPA built into
# the bootJar). Transitively builds :pipeline:app:package. Output: webapp/app/build/dist-app/pdfbook-web/.
# Linux needs no -Pp4suta.nativePrefix (the convention resolves Leptonica/poppler/qpdf from the dev
# image's standard dirs); the cross-OS CI legs pass a prefix for their MSYS2 / Homebrew natives.
web-package:
    {{gradlew}} :webapp:app:package {{gradle_flags}}

# Run the built webapp app-image's server (after `just web-package`). PATH-EMPTY by design: the
# launcher's baked -Dp4suta.pdfbook.path resolves the NESTED pdfbook, so it needs no native toolchain
# on PATH — the whole point of the self-contained image. Binds 0.0.0.0 for port-forwarding; override
# the host port with WEB_PORT. Runs the dev container's Linux launcher (the Win/macOS images run on
# their own host).
web-app-run: web-package
    #!/usr/bin/env bash
    set -euo pipefail
    run='exec webapp/app/build/dist-app/pdfbook-web/bin/pdfbook-web --server.address=0.0.0.0'
    if [ "{{inside}}" = "1" ]; then
        exec bash -lc "$run"
    fi
    docker rm -f pdfbook-web-app >/dev/null 2>&1 || true
    exec docker compose run --rm --name pdfbook-web-app -p {{web_port}}:8080 dev bash -lc "$run"

# ----- cross-OS self-contained app-images -----

# Build a self-contained jpackage app-image for an app, on the CURRENT OS. See docs/distribution.md
# for the build models, per-app native requirements, and the property scheme.
#   just dist-package pdfbook | register | despeckle | tate | webapp
# Linux builds in the dev container with NO native prefix (the convention resolves Leptonica/poppler/
# qpdf from the dev image's standard dirs). macOS/Windows build with the HOST Gradle (jpackage emits an
# OS-native launcher a Linux container cannot), pointed at the host natives via -Pp4suta.nativePrefix
# (macOS=$(brew --prefix), Windows=C:\msys64\mingw64) — install the prerequisites first. The cross-OS
# legs are CI-verified in distribution.yml; locally only the Linux path runs end-to-end.
dist-package APP:
    #!/usr/bin/env bash
    set -euo pipefail
    case "{{APP}}" in
        register)  project=":register:app:package" ;;
        despeckle) project=":despeckle:app:package" ;;
        pdfbook)   project=":pipeline:app:package" ;;
        tate)      project=":tate-yoko-pdf:app:package" ;;
        webapp)    project=":webapp:app:package" ;;
        *) echo "unknown app '{{APP}}' — one of: register despeckle pdfbook tate webapp" >&2; exit 2 ;;
    esac
    case "{{os()}}" in
        linux)   exec {{gradlew}} "$project" {{gradle_flags}} ;;
        macos)   exec ./gradlew "$project" {{gradle_flags}} "-Pp4suta.nativePrefix=$(brew --prefix)" ;;
        windows) exec ./gradlew "$project" {{gradle_flags}} '-Pp4suta.nativePrefix=C:\msys64\mingw64' ;;
        *) echo "unsupported OS: {{os()}}" >&2; exit 2 ;;
    esac

# ----- format / lint (mirrors CI + the lefthook gates) -----

# Auto-format everything in place (spelling included). yamlfmt gets NO path arg: a path overrides
# the .yamlfmt include/exclude (so it would reformat node_modules / pnpm-lock.yaml); with no arg it
# honors the config's globs.
fmt:
    {{gradlew}} spotlessApply {{gradle_flags}}
    {{taplo}} fmt
    {{biome}} check --write .
    {{sh_run}} 'cd webapp/frontend && pnpm install --frozen-lockfile && pnpm run format'
    {{yamlfmt}}
    {{typos}} --write-changes

# Verify formatting without writing (what CI checks).
fmt-check:
    {{gradlew}} spotlessCheck {{gradle_flags}}
    {{taplo}} fmt --check
    {{biome}} ci --error-on-warnings .
    {{sh_run}} 'cd webapp/frontend && pnpm install --frozen-lockfile && pnpm run format:check'
    {{yamlfmt}} --lint

# Spell-check and fix in place.
typos:
    {{typos}} --write-changes

# Spell-check without writing (what CI and the pre-push gate run).
typos-check:
    {{typos}}

# Lint every GitHub Actions workflow in the repo (.github/workflows/; depth-agnostic find).
actionlint:
    #!/usr/bin/env bash
    set -uo pipefail
    files=$(find . -path '*/.github/workflows/*.yml' -not -path '*/build/*' | sort)
    [ -z "$files" ] && { echo "no GitHub Actions workflows found"; exit 0; }
    {{actionlint}} $files

# Aggregated lint gate (mirrors CI's lint-peripheral plus Spotless).
lint: fmt-check typos-check actionlint

# ----- coverage / dependencies / rewrite -----

# Whole-build aggregated JaCoCo coverage report (build/reports/jacoco/testCodeCoverageReport/).
coverage:
    {{gradlew}} testCodeCoverageReport {{gradle_flags}}

# Report dependency + Gradle updates (ben-manes) and diff non-Gradle pinned versions.
# ben-manes 0.54 is not configuration-cache safe, so it runs with --no-configuration-cache.
outdated:
    {{gradlew}} dependencyUpdates {{gradle_flags}} --no-configuration-cache

# Run the advisory OpenRewrite recipes in place, then re-impose the google-java-format layout.
rewrite:
    {{gradlew}} rewriteRun {{gradle_flags}}
    {{gradlew}} spotlessApply {{gradle_flags}}

# ----- git hooks -----

hooks:
    {{lefthook}} install

# ----- lefthook delegated recipes (invoked by git hooks; do not run directly) -----

_hook-spotless-apply:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ spotless / google-java-format — reformatting Java sources"
    {{gradlew}} spotlessApply {{gradle_flags}} && exit 0
    echo "✗ spotless could not run (no JDK in the image, or .gradle-home not writable)." >&2
    exit 1

_hook-typos-fix +files:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ typos — spell-checking staged files"
    {{typos}} --force-exclude --write-changes {{files}} && exit 0
    echo "✗ typos found misspellings it could not auto-fix (listed above)." >&2
    exit 1

_hook-taplo-fmt +files:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ taplo — formatting TOML"
    {{taplo}} fmt {{files}} && exit 0
    echo "✗ taplo failed to format TOML (see above)." >&2
    exit 1

_hook-biome-format +files:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ biome — formatting JSON"
    {{biome}} format --write {{files}} && exit 0
    echo "✗ biome failed to format JSON (see above)." >&2
    exit 1

_hook-yamlfmt +files:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ yamlfmt — formatting YAML"
    # Explicit paths bypass the .yamlfmt exclude globs, so drop pnpm's lockfile here — it is valid
    # YAML but pnpm owns its layout and reformatting it would corrupt the install.
    files=$(printf '%s\n' {{files}} | grep -vE '(^|/)pnpm-lock\.yaml$' || true)
    [ -z "$files" ] && { echo "  (no YAML to format after exclusions)"; exit 0; }
    {{yamlfmt}} $files && exit 0
    echo "✗ yamlfmt failed to format YAML (see above)." >&2
    exit 1

_hook-actionlint +files:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ actionlint — linting GitHub Actions workflows"
    {{actionlint}} {{files}} && exit 0
    echo "✗ actionlint found workflow problems (listed above)." >&2
    exit 1
