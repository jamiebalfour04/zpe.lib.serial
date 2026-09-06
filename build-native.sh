#!/usr/bin/env bash
set -euo pipefail

GRAAL_HOME="${GRAAL_HOME:-${GRAALVM_HOME:-$HOME/.sdkman/candidates/java/current}}"
JSERIALCOMM_VERSION="2.11.4"
JSERIALCOMM_JAR="${JSERIALCOMM_JAR:-lib/jSerialComm-$JSERIALCOMM_VERSION.jar}"
BUILD_DIR="${BUILD_DIR:-build/native}"
PLATFORM="$(uname -s)"

if [[ "$PLATFORM" == MINGW* || "$PLATFORM" == MSYS* || "$PLATFORM" == CYGWIN* ]]; then
  GRAAL_HOME="$(cygpath -u "$GRAAL_HOME")"
  JAVAC="${JAVAC:-$GRAAL_HOME/bin/javac.exe}"
  NATIVE_IMAGE="${NATIVE_IMAGE:-$GRAAL_HOME/bin/native-image.cmd}"
  CP_SEPARATOR=';'
else
  JAVAC="${JAVAC:-$GRAAL_HOME/bin/javac}"
  NATIVE_IMAGE="${NATIVE_IMAGE:-$GRAAL_HOME/bin/native-image}"
  CP_SEPARATOR=':'
fi

if [ ! -f "$NATIVE_IMAGE" ] && command -v native-image >/dev/null 2>&1; then
  NATIVE_IMAGE="$(command -v native-image)"
  JAVAC="$(command -v javac)"
fi
if [ ! -f "$NATIVE_IMAGE" ]; then
  printf 'GraalVM native-image was not found. Set GRAAL_HOME or GRAALVM_HOME.\n' >&2
  exit 1
fi

if [ ! -f "$JSERIALCOMM_JAR" ]; then
  mkdir -p "$(dirname "$JSERIALCOMM_JAR")"
  curl -fL "https://repo1.maven.org/maven2/com/fazecast/jSerialComm/$JSERIALCOMM_VERSION/jSerialComm-$JSERIALCOMM_VERSION.jar" -o "$JSERIALCOMM_JAR"
fi

mkdir -p "$BUILD_DIR/classes"
"$JAVAC" -cp "$JSERIALCOMM_JAR" -d "$BUILD_DIR/classes" native-src/SerialNativePlugin.java
"$NATIVE_IMAGE" --shared --no-fallback --enable-native-access=ALL-UNNAMED \
  -cp "$BUILD_DIR/classes$CP_SEPARATOR$JSERIALCOMM_JAR" \
  -H:+UnlockExperimentalVMOptions \
  -H:Path="$BUILD_DIR" \
  -H:Name=zpe.lib.serial \
  -H:JNIConfigurationFiles=native-config/jni-config.json \
  -H:IncludeResources='(OSX|Linux|Windows)/.*' \
  -H:-UnlockExperimentalVMOptions \
  SerialNativePlugin

case "$PLATFORM" in
  Darwin)
    mv "$BUILD_DIR/libzpe.lib.serial.dylib" "$BUILD_DIR/zpe.lib.serial.dylib"
    OUTPUT="$BUILD_DIR/zpe.lib.serial.dylib"
    ;;
  Linux)
    mv "$BUILD_DIR/libzpe.lib.serial.so" "$BUILD_DIR/zpe.lib.serial.so"
    OUTPUT="$BUILD_DIR/zpe.lib.serial.so"
    ;;
  MINGW*|MSYS*|CYGWIN*) OUTPUT="$BUILD_DIR/zpe.lib.serial.dll" ;;
  *) OUTPUT="$BUILD_DIR/zpe.lib.serial.dll" ;;
esac

if [ ! -f "$OUTPUT" ]; then
  printf 'Native plugin was not produced at %s\n' "$OUTPUT" >&2
  exit 1
fi
printf 'Built %s\n' "$OUTPUT"
