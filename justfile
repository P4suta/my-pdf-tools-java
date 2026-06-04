# my-pdf-tools-java (monorepo root) — task entry points.
#
# Routes through the unified dev container unless INSIDE_CONTAINER=1, mirroring
# the per-app justfiles (register/justfile, despeckle/justfile). The dev image
# (./Dockerfile: register-dev + fontconfig) sets INSIDE_CONTAINER=1, so the same
# recipes call ./gradlew directly inside the container instead of re-entering
# Docker. Host machines need nothing but Docker.
#
# NOTE: there is no root Gradle build yet — each app (register/, despeckle/,
# tate-yoko-pdf/) is still its own standalone build. These recipes are the
# scaffolding for the unified composite build that Phase 3 introduces; until a
# root settings.gradle.kts + gradlew exist, run the per-app justfiles.

inside := env_var_or_default("INSIDE_CONTAINER", "0")

dev_running := `docker compose ps --status running --services 2>/dev/null | grep -c '^dev$' 2>/dev/null || true`
docker_run := if dev_running == "0" { "docker compose run --rm dev" } else { "docker compose exec dev" }

gradlew := if inside == "1" { "./gradlew" } else { docker_run + " ./gradlew" }

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

# Open an interactive shell in the dev container.
shell:
    {{docker_run}} bash

# Passthrough: run an arbitrary Gradle invocation in the dev container.
#   just gradle :register:app:installDist
gradle *ARGS:
    {{gradlew}} {{ARGS}} {{gradle_flags}}

# ----- format / lint (mirrors CI + the lefthook gates) -----

# Auto-format everything in place (spelling included).
fmt:
    {{gradlew}} spotlessApply {{gradle_flags}}
    {{taplo}} fmt
    {{biome}} format --write .
    {{yamlfmt}} .
    {{typos}} --write-changes

# Verify formatting without writing (what CI checks).
fmt-check:
    {{gradlew}} spotlessCheck {{gradle_flags}}
    {{taplo}} fmt --check
    {{biome}} format .
    {{yamlfmt}} --lint .

# Spell-check and fix in place.
typos:
    {{typos}} --write-changes

# Spell-check without writing (what CI and the pre-push gate run).
typos-check:
    {{typos}}

# Lint every GitHub Actions workflow in the repo. Per-app .github/ is kept until
# the CI/Docker cutover, so workflows live at <app>/.github/workflows/; this finds
# them at any depth (a future root .github/workflows is covered too).
actionlint:
    #!/usr/bin/env bash
    set -uo pipefail
    files=$(find . -path '*/.github/workflows/*.yml' -not -path '*/build/*' | sort)
    [ -z "$files" ] && { echo "no GitHub Actions workflows found"; exit 0; }
    {{actionlint}} $files

# Aggregated lint gate (mirrors CI's lint-peripheral plus Spotless).
lint: fmt-check typos-check actionlint

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
    {{yamlfmt}} {{files}} && exit 0
    echo "✗ yamlfmt failed to format YAML (see above)." >&2
    exit 1

_hook-actionlint +files:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "▶ actionlint — linting GitHub Actions workflows"
    {{actionlint}} {{files}} && exit 0
    echo "✗ actionlint found workflow problems (listed above)." >&2
    exit 1
