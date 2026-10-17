#!/usr/bin/env bash
set -euo pipefail

# Findex Release Build Script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Findex Release APK Builder ==="

# Key configuration
export ANDROID_KEYSTORE_FILE="${ANDROID_KEYSTORE_FILE:-$HOME/Keys/aiyu-soft-android-keys.jsk}"
export ANDROID_KEYSTORE_PASSWORD="${ANDROID_KEYSTORE_PASSWORD:-akaAiyu@03}"
export ANDROID_KEY_ALIAS="${ANDROID_KEY_ALIAS:-findex}"
export ANDROID_KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-akaAiyu@03}"

if [ ! -f "$ANDROID_KEYSTORE_FILE" ]; then
    echo "ERROR: Keystore file not found at: $ANDROID_KEYSTORE_FILE"
    exit 1
fi

echo "Using Keystore: $ANDROID_KEYSTORE_FILE"
echo "Using Key Alias: $ANDROID_KEY_ALIAS"
echo "Minify (R8): Enabled"
echo "Resource Shrinking: Enabled"
echo ""

echo "--> Building release APK..."
./gradlew :app:assembleRelease

APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=== Release APK Built Successfully! ==="
    echo "File: $APK_PATH"
    echo "Size: $(du -h "$APK_PATH" | cut -f1)"
    echo "SHA-256: $(sha256sum "$APK_PATH" | cut -d' ' -f1)"
    echo ""

    # Optional signature check if apksigner exists in SDK
    APKSIGNER=$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1 || true)
    if [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
        echo "--> Verifying signature with apksigner..."
        "$APKSIGNER" verify --verbose "$APK_PATH" || true
    fi
    echo ""
    echo "Ready for installation or distribution."
else
    echo "ERROR: Release APK not found at expected location: $APK_PATH"
    exit 1
fi
