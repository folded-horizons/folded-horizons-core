FROM mcr.microsoft.com/devcontainers/java:21

# Gradle toolchain downloads (foojay resolver) need certificates + git
RUN apt-get update && apt-get install -y --no-install-recommends git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
