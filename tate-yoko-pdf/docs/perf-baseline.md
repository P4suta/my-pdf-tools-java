# CI / build performance baseline

Rolling history of CI and local build wall-clock times. Phase 1 of the
optimization plan sets this baseline so future phases can be evaluated
quantitatively.

CI numbers come from `gh run list --workflow=ci.yml --branch=main`. The
`perf report` job in `.github/workflows/ci.yml` publishes a fresh median
to every run's Job Summary, so this file only needs manual updates when a
phase lands.

## Pre-optimization baseline (2026-05-27)

Source: 20 most-recent main runs prior to the Phase 1 PR.

### GitHub Actions

| Metric | Value |
| --- | --- |
| Full CI wall-clock — median | **433 s** (7 m 13 s) |
| Full CI wall-clock — min / max | 260 s / 563 s |
| Critical path | `check (JVM) 94 s → native-image-macOS 462 s` ≈ 556 s |
| `typos` | ~4 s |
| `check (JVM)` median | 94 s |
| `native-image (ubuntu-latest)` median | 255 s |
| `native-image (macos-latest)` median | 295 s (max 462 s) |
| `native-image (windows-latest)` median | 301 s |
| `native-image` outliers | 1220 s (cold-cache runs) |

The macOS run dominates because macOS/Windows still use stock GraalVM CE
while Ubuntu uses Liberica NIK inside Docker (`ci.yml:131-160` comment).

### Local (Docker dev container)

Measured on the host described below; all commands run via the existing
`docker compose run --rm dev …` pattern (no daemon reuse across invocations).

| Command | Wall-clock |
| --- | --- |
| `just check` (cold) | TBD — fill in once Phase 1 lands |
| `just check` (warm) | TBD |
| `just test` | TBD |
| `just outdated` | TBD |
| `just native` | TBD |

Host: Linux 6.8.0-117-generic, Docker dev image based on
`bellsoft/liberica-native-image-kit-container:jdk-25-nik-25-glibc`.

## Targets

| Stage | Target | Reached at |
| --- | --- | --- |
| Phase 1–3 (measurement + cross-CI cache) | CI median < 5 m | — |
| Phase 1–7 (full plan complete) | CI median < 4 m | — |
| Local `just check` warm | -30 s vs. baseline | — |

## History

| Date | Phase landed | CI median (s) | macOS native (s) | check JVM (s) | Notes |
| --- | --- | --- | --- | --- | --- |
| 2026-05-27 | — (baseline) | 433 | 295 | 94 | Pre-optimization. |
