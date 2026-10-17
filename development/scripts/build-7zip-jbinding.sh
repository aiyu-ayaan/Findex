#!/usr/bin/env bash
# Rebuilds lib7-Zip-JBinding.so for core/src/main/jniLibs with 16 KB page alignment.
# The published 7-Zip-JBinding-4Android AAR (Release-16.02-2.02) ships 4 KB-aligned
# libraries, which Android 15+ devices with 16 KB pages refuse to load.
# Needs: Android NDK r28+, cmake + ninja on PATH (e.g. `pip install cmake ninja` in a venv).
set -euo pipefail

TAG="Release-16.02-2.02" # keep in sync with sevenZipJBinding in gradle/libs.versions.toml
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SDK="${ANDROID_HOME:-$(grep sdk.dir "$ROOT/local.properties" | cut -d= -f2)}"
NDK="${NDK:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone -q --depth 1 --branch "$TAG" https://github.com/omicronapps/7-Zip-JBinding-4Android.git "$WORK/src"
for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
  cmake -S "$WORK/src/sevenzipjbinding" -B "$WORK/build-$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_CXX_FLAGS="-std=gnu++14" \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" >/dev/null
  cmake --build "$WORK/build-$ABI" >/dev/null
  OUT="$ROOT/core/src/main/jniLibs/$ABI/lib7-Zip-JBinding.so"
  mkdir -p "$(dirname "$OUT")"
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded -o "$OUT" "$WORK/build-$ABI/lib7-Zip-JBinding.so"
  echo "$ABI: LOAD align $(readelf -lW "$OUT" | awk '/LOAD/{print $NF}' | sort -u | tr '\n' ' ')"
done
