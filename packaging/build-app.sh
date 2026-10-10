#!/usr/bin/env bash
# Bouwt een zelfstandige app (met eigen Java erin) in build/app/Reeksoptimalisatie.
# Draait op de computer waarvoor je bouwt: op Windows geeft dit Reeksoptimalisatie.exe.
# Vereist JDK 21 en Maven; op GitHub (Actions) is dat allemaal aanwezig.
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -B package -DskipTests
rm -rf build
mkdir -p build/input
cp target/reeks-solver-1.0.0.jar build/input/reeks-solver.jar
cp target/lib/*.jar build/input/
jpackage --type app-image \
  --name Reeksoptimalisatie \
  --app-version 1.0.0 \
  --input build/input \
  --main-jar reeks-solver.jar \
  --main-class be.reeks.Main \
  --arguments gui \
  --java-options "-Xmx1g" \
  --add-modules java.se,jdk.httpserver,jdk.unsupported,jdk.management \
  --dest build/app
echo "Klaar: build/app/Reeksoptimalisatie"
