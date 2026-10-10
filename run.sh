#!/usr/bin/env bash
# Start de reeksoptimalisatie in een container. Op je systeem is enkel podman nodig.
# Daarna: open http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")"
ENGINE="${CONTAINER_ENGINE:-podman}"
PORT="${PORT:-8080}"
IMAGE="docker.io/library/maven:3.9-eclipse-temurin-21"
mkdir -p target

if command -v xdg-open >/dev/null 2>&1; then
  (sleep 12; xdg-open "http://localhost:${PORT}" >/dev/null 2>&1 || true) &
fi

exec "$ENGINE" run --rm -it --name reeks-solver \
  -p "127.0.0.1:${PORT}:8080" \
  -v "$PWD":/work:Z -v reeks-m2:/root/.m2 -w /work \
  "$IMAGE" bash -c '
    if [ ! -f target/classes/be/reeks/Main.class ] || [ -n "$(find src pom.xml -newer target/classes/be/reeks/Main.class | head -1)" ]; then
      mvn -q -B package -DskipTests
    fi
    exec java -Xmx1g -cp "target/classes:target/lib/*" be.reeks.Main'
