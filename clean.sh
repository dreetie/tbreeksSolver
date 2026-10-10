#!/usr/bin/env bash
# Ruimt alles op wat run.sh heeft aangemaakt (image, Maven-cache, build-map).
set -euo pipefail
cd "$(dirname "$0")"
ENGINE="${CONTAINER_ENGINE:-podman}"
"$ENGINE" rm -f reeks-solver 2>/dev/null || true
"$ENGINE" volume rm reeks-m2 2>/dev/null || true
"$ENGINE" rmi docker.io/library/maven:3.9-eclipse-temurin-21 2>/dev/null || true
rm -rf target
echo "Opgeruimd."
