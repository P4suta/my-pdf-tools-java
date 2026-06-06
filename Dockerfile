# Unified monorepo dev image — self-contained, builds ALL FIVE features' full
# gates from one image. No longer an overlay on any per-app image: this Dockerfile
# carries the complete toolchain so the monorepo stands alone.
#
# It consolidates register's full toolchain (the richest of the original per-app
# images) with tate-yoko-pdf's font stack:
#   - Liberica JDK 25 Full (BellSoft). The **Full** flavor ships the jmods/
#     directory that jlink/jpackage consume to assemble the bundled JRE for the
#     `just package` app-image distribution; Temurin's image omits jmods. FFM is
#     final since 22; 25 is the current LTS.
#   - Leptonica (liblept.so.5) — the register core calls it through FFM.
#   - The scan-pipeline PDF toolbox: poppler-utils (pdfimages/pdfinfo/pdftoppm),
#     qpdf, and jbig2enc (built from source) for the lossless-JBIG2 repack that
#     `register pipeline` drives — pure Java now, no Python.
#   - libwebp tools (img2webp) for the --diag --flipbook animated-WebP overlay.
#   - binutils (objcopy, for jlink --strip-debug) and patchelf (RPATH=$ORIGIN on
#     the natives bundled into the app-image by `stageNatives`).
#   - fontconfig + FreeType + DejaVu fonts — tate-yoko-pdf's PDFBox rendering
#     tests need a working fontconfig/FreeType/font stack so fc-list/fc-cache
#     work and PDFBox has font config to read.
#   - The language-agnostic quality tools (typos, taplo, biome, yamlfmt,
#     actionlint, lefthook, just).
#
# Gradle itself is NOT installed: the committed wrapper (./gradlew) fetches the
# pinned distribution, so the build is reproducible from the repo alone.

# syntax=docker/dockerfile:1.7

# ----- jbig2enc: lossless JBIG2 encoder, built from source -----
# Not packaged for Debian/Ubuntu, so it is built here. Lossless JBIG2 (generic
# region coding — never the lossy symbol substitution) lets `just to-pdf` pack
# registered bitonal pages far tighter than CCITT G4 while staying pixel-exact.
# Built in a throwaway stage so the dev image carries only the resulting `jbig2`
# binary, never the C/C++ toolchain.
FROM eclipse-temurin:25-jdk-noble AS jbig2enc-build

ENV DEBIAN_FRONTEND=noninteractive
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        autoconf \
        automake \
        build-essential \
        ca-certificates \
        git \
        libleptonica-dev \
        libtool \
        pkg-config \
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

# Pinned to a release tag so the image is reproducible from the repo alone.
ARG JBIG2ENC_VERSION=0.31
RUN git clone --depth 1 --branch "${JBIG2ENC_VERSION}" \
        https://github.com/agl/jbig2enc /tmp/jbig2enc \
    && cd /tmp/jbig2enc \
    && ./autogen.sh \
    && ./configure \
    && make -j"$(nproc)" \
    && make install DESTDIR=/dist

FROM ubuntu:24.04 AS dev

ARG USER_UID=1000
ARG USER_GID=1000
ENV DEBIAN_FRONTEND=noninteractive

# Fail a RUN if any stage of a pipe fails (e.g. curl | tar), not just the last.
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]

# System libraries + scan-pipeline tools + fonts + the JDK. libleptonica-dev
# pulls in the runtime liblept.so.5 that FFM loads at run time; poppler-utils
# (pdfimages / pdfinfo), qpdf and jbig2enc drive the `register pipeline` PDF
# flow; webp provides img2webp for the --diag --flipbook overlay. binutils
# (objcopy) backs jlink --strip-debug and patchelf rewrites the bundled natives'
# RPATH for the app-image. fontconfig + libfreetype6 + fonts-dejavu give
# tate-yoko-pdf's PDFBox rendering tests a working font stack (fc-list/fc-cache).
# The JDK is Liberica 25 **Full** from BellSoft's apt repo — the Full flavor
# ships jmods/, which jlink/jpackage need (Temurin omits them).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        binutils \
        ca-certificates \
        curl \
        fontconfig \
        fonts-dejavu \
        git \
        gnupg \
        libfreetype6 \
        libimage-exiftool-perl \
        libleptonica-dev \
        patchelf \
        poppler-utils \
        qpdf \
        sudo \
        unzip \
        webp \
    && curl -fsSL https://download.bell-sw.com/pki/GPG-KEY-bellsoft \
        | gpg --dearmor -o /usr/share/keyrings/bellsoft.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/bellsoft.gpg] https://apt.bell-sw.com/ stable main" \
        > /etc/apt/sources.list.d/bellsoft.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends bellsoft-java25-full \
    && rm -rf /var/lib/apt/lists/*

# Liberica Full installs here on amd64; jlink/jpackage live in $JAVA_HOME/bin and
# consume $JAVA_HOME/jmods. Fail the build early if a Lite JDK (no jmods) somehow
# landed instead — otherwise it would only surface at `just package` time.
ENV JAVA_HOME=/usr/lib/jvm/bellsoft-java25-full-amd64
ENV PATH=$JAVA_HOME/bin:$PATH
RUN test -d "$JAVA_HOME/jmods"

# The ubuntu base ships no locale, so the JVM defaults sun.jnu.encoding to ASCII
# and chokes decoding the non-ASCII (Japanese) scan filenames the pipeline is fed.
# C.UTF-8 is built into glibc — no locales package needed.
ENV LANG=C.UTF-8

# jbig2enc's `jbig2` binary, built in the stage above. It dynamically links the
# runtime liblept.so.5 that libleptonica-dev just installed.
COPY --from=jbig2enc-build /dist/usr/local/bin/jbig2 /usr/local/bin/jbig2

# ----- language-agnostic quality tools (pinned static binaries) -----

# just (command runner).
ARG JUST_VERSION=1.51.0
RUN curl -fsSL "https://github.com/casey/just/releases/download/${JUST_VERSION}/just-${JUST_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
    | tar xz -C /usr/local/bin just

# lefthook (git hooks runner) — .deb release.
ARG LEFTHOOK_VERSION=2.1.9
RUN curl -fsSL -o /tmp/lefthook.deb \
        "https://github.com/evilmartians/lefthook/releases/download/v${LEFTHOOK_VERSION}/lefthook_${LEFTHOOK_VERSION}_amd64.deb" \
    && dpkg -i /tmp/lefthook.deb \
    && rm /tmp/lefthook.deb

# typos (spell-checker).
ARG TYPOS_VERSION=1.47.0
RUN curl -fsSL "https://github.com/crate-ci/typos/releases/download/v${TYPOS_VERSION}/typos-v${TYPOS_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
    | tar xz -C /usr/local/bin ./typos \
    && chmod +x /usr/local/bin/typos

# taplo (TOML formatter) — gzipped single binary.
ARG TAPLO_VERSION=0.10.0
RUN curl -fsSL "https://github.com/tamasfe/taplo/releases/download/${TAPLO_VERSION}/taplo-linux-x86_64.gz" \
    | gunzip > /usr/local/bin/taplo \
    && chmod +x /usr/local/bin/taplo

# biome (JSON formatter) — single binary; the v2 release tag is `@biomejs/biome@<ver>`.
ARG BIOME_VERSION=2.4.16
RUN curl -fsSL "https://github.com/biomejs/biome/releases/download/@biomejs/biome@${BIOME_VERSION}/biome-linux-x64" \
        -o /usr/local/bin/biome \
    && chmod +x /usr/local/bin/biome

# yamlfmt (YAML formatter).
ARG YAMLFMT_VERSION=0.21.0
RUN curl -fsSL "https://github.com/google/yamlfmt/releases/download/v${YAMLFMT_VERSION}/yamlfmt_${YAMLFMT_VERSION}_Linux_x86_64.tar.gz" \
    | tar xz -C /usr/local/bin yamlfmt

# actionlint (GitHub Actions linter).
ARG ACTIONLINT_VERSION=1.7.12
RUN curl -fsSL "https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/actionlint_${ACTIONLINT_VERSION}_linux_amd64.tar.gz" \
    | tar xz -C /usr/local/bin actionlint

# ----- Node.js + pnpm (the SPA toolchain) -----
# The webapp/frontend SPA builds and runs INSIDE this container like everything else
# (build-runs-only-in-docker) — there is no host Node, so no host-vs-container pnpm split and nothing
# about the lockfile to keep in sync by hand. pnpm is baked in at a pinned version (NOT corepack:
# corepack resolves the version at runtime and verifies a signature against keys bundled in Node,
# which go stale and fail — exactly the kind of thing the build should never make anyone babysit).
# PNPM_VERSION must equal the frontend's `packageManager` field; pnpm itself warns loudly on drift.
# node rides the latest Active LTS line (a build toolchain should ride LTS, not the Current line);
# checkExtraVersions tracks the latest LTS. pnpm must equal the frontend's packageManager field.
ARG NODE_VERSION=24.16.0
ARG PNPM_VERSION=11.5.1
RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.gz" \
        | tar xz -C /usr/local --strip-components=1 \
    && npm install -g "pnpm@${PNPM_VERSION}" \
    && npm cache clean --force

# Match host UID so bind-mounted files don't end up root-owned. The ubuntu base
# already has a user/group at 1000 (`ubuntu`); reuse it when the requested IDs
# collide, otherwise create a fresh `dev` user.
RUN set -eux; \
    if getent group "${USER_GID}" >/dev/null; then \
        groupname="$(getent group "${USER_GID}" | cut -d: -f1)"; \
    else \
        groupname=dev; groupadd --gid "${USER_GID}" dev; \
    fi; \
    if getent passwd "${USER_UID}" >/dev/null; then \
        username="$(getent passwd "${USER_UID}" | cut -d: -f1)"; \
    else \
        username=dev; useradd --uid "${USER_UID}" --gid "${USER_GID}" -m dev; \
    fi; \
    echo "${username} ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers; \
    echo "DEV_USER=${username}" > /etc/p4suta-dev-user

USER ${USER_UID}:${USER_GID}
ENV INSIDE_CONTAINER=1 \
    GRADLE_USER_HOME=/workspace/.gradle-home
WORKDIR /workspace
CMD ["bash"]
