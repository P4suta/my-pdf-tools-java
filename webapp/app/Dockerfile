# syntax=docker/dockerfile:1.7
#
# Runtime image for the pdfbook web server. Distinct from the repo-root ./Dockerfile (the dev/build
# image): this one produces a small, runnable server.
#
#   - The server is a Spring Boot bootJar (subprocess design — no FFM in the server JVM).
#   - It shells out to the packaged pdfbook CLI, which DOES use the native toolbox (Leptonica via
#     FFM, pdfimages/qpdf/jbig2/img2webp, fonts), so those live in the runtime layer.
#   - pdfbook is resolved as an external tool via the PATH (see ADR-0009).
#
# Build (context = repo root):
#   docker build -f webapp/app/Dockerfile -t pdfbook-web .
# Run (kept local; drop the 127.0.0.1 prefix to expose on the LAN — add auth first):
#   docker run --rm -p 127.0.0.1:8080:8080 pdfbook-web

# ----- stage 1: jbig2enc (lossless JBIG2 encoder, built from source; not packaged for Debian) -----
FROM eclipse-temurin:25-jdk-noble AS jbig2enc
ENV DEBIAN_FRONTEND=noninteractive
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        autoconf automake build-essential ca-certificates git \
        libleptonica-dev libtool pkg-config zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*
ARG JBIG2ENC_VERSION=0.31
RUN git clone --depth 1 --branch "${JBIG2ENC_VERSION}" https://github.com/agl/jbig2enc /tmp/jbig2enc \
    && cd /tmp/jbig2enc \
    && ./autogen.sh && ./configure && make -j"$(nproc)" && make install DESTDIR=/dist

# ----- stage 2: build the server bootJar + the pdfbook distribution -----
# A plain JDK 25 is enough: bootJar and installDist are compile+package tasks (no tests, no jmods).
FROM eclipse-temurin:25-jdk-noble AS build
ENV GRADLE_USER_HOME=/src/.gradle
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon --console=plain \
        -Dorg.gradle.java.installations.auto-download=false \
        :webapp:app:bootJar :pipeline:app:installDist \
    && cp "$(ls webapp/app/build/libs/webapp-app-*.jar | grep -v plain)" /webapp.jar

# ----- stage 3: runtime -----
FROM eclipse-temurin:25-jre-noble AS runtime
ENV DEBIAN_FRONTEND=noninteractive
# The ubuntu base ships no locale; C.UTF-8 (built into glibc) lets the JVM decode non-ASCII
# (Japanese) scan filenames the pipeline is fed.
ENV LANG=C.UTF-8
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]

# Only the runtime natives pdfbook needs — no build tools, no jmods. libleptonica-dev provides the
# runtime liblept.so.5 that pdfbook loads via FFM; poppler-utils/qpdf/webp/exiftool are the PDF
# toolbox; fontconfig + freetype + DejaVu give PDFBox a working font stack.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates fontconfig fonts-dejavu libfreetype6 libimage-exiftool-perl \
        libleptonica-dev poppler-utils qpdf webp \
    && rm -rf /var/lib/apt/lists/*

COPY --from=jbig2enc /dist/usr/local/bin/jbig2 /usr/local/bin/jbig2
# The pdfbook CLI distribution + its launcher on PATH; the server resolves "pdfbook" from PATH.
COPY --from=build /src/pipeline/app/build/install/pdfbook /opt/pdfbook
ENV PATH=/opt/pdfbook/bin:$PATH
COPY --from=build /webapp.jar /opt/app/webapp.jar

RUN useradd --create-home --uid 1000 app
USER app

EXPOSE 8080
# Inside the container, bind to all interfaces; publish locally with `-p 127.0.0.1:8080:8080`.
ENV SERVER_ADDRESS=0.0.0.0
ENV APP_WORK_DIR=/tmp/pdfbook-web/jobs
ENTRYPOINT ["java", "-jar", "/opt/app/webapp.jar"]
