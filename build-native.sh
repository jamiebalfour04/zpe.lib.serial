#!/usr/bin/env bash
set -euo pipefail

GRAAL_HOME="${GRAAL_HOME:-$HOME/.sdkman/candidates/java/current}"
JSERIALCOMM_VERSION="2.11.4"
JSERIALCOMM_JAR="${JSERIALCOMM_JAR:-lib/jSerialComm-$JSERIALCOMM_VERSION.jar}"
BUILD_DIR="${BUILD_DIR:-build/native}"

if [ ! -f "$JSERIALCOMM_JAR" ]; then
  mkdir -p "$(dirname "$JSERIALCOMM_JAR")"
  curl -fL "https://repo1.maven.org/maven2/com/fazecast/jSerialComm/$JSERIALCOMM_VERSION/jSerialComm-$JSERIALCOMM_VERSION.jar" -o "$JSERIALCOMM_JAR"
fi

mkdir -p "$BUILD_DIR/classes"
"$GRAAL_HOME/bin/javac" -cp "$JSERIALCOMM_JAR" -d "$BUILD_DIR/classes" native-src/SerialNativePlugin.java
"$GRAAL_HOME/bin/native-image" --shared --no-fallback --enable-native-access=ALL-UNNAMED \
  -cp "$BUILD_DIR/classes:$JSERIALCOMM_JAR" \
  -H:Path="$BUILD_DIR" \
  -H:Name=zpe.lib.serial \
  -H:JNIConfigurationFiles=native-config/jni-config.json \
  -H:IncludeResources='(OSX|Linux|Windows)/.*' \
  SerialNativePlugin

case "$(uname -s)" in
  Darwin)
    mv "$BUILD_DIR/libzpe.lib.serial.dylib" "$BUILD_DIR/zpe.lib.serial.dylib"
    OUTPUT="$BUILD_DIR/zpe.lib.serial.dylib"
    ;;
  Linux)
    mv "$BUILD_DIR/libzpe.lib.serial.so" "$BUILD_DIR/zpe.lib.serial.so"
    OUTPUT="$BUILD_DIR/zpe.lib.serial.so"
    ;;
  *) OUTPUT="$BUILD_DIR/zpe.lib.serial.dll" ;;
esac

printf 'Built %s\n' "$OUTPUT"
