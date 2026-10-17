#!/usr/bin/env bash
set -e

# ==============================================================================
# Findex - Android Build & Emulator Runner
# Works standalone without Android Studio installed.
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

PACKAGE_NAME="com.aiyu.findex.debug"
ACTIVITY_NAME="com.aiyu.findex.MainActivity"

LOGS=0
DO_CLEAN=0
TARGET_AVD=""
RESET_DISPLAY=0

show_help() {
    cat << EOF
Usage: ./run.sh [OPTIONS]

Options:
  --logs, -l            Stream app logcat after launching
  --clean, -c           Clean build before assembling
  --reset-display, -r   Reset emulator display size and density to physical defaults
  --avd <name>          Specify an Android Virtual Device (AVD) to launch if none is running
  --help, -h            Show this help message

Examples:
  ./run.sh                  Build, install, and launch on connected emulator or device
  ./run.sh --logs           Build, launch, and follow logcat output
  ./run.sh --clean --logs   Clean build and run with logs
  ./run.sh --reset-display  Reset emulator resolution/DPI and run
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --logs|-l)
            LOGS=1
            shift
            ;;
        --clean|-c)
            DO_CLEAN=1
            shift
            ;;
        --reset-display|-r)
            RESET_DISPLAY=1
            shift
            ;;
        --avd)
            TARGET_AVD="$2"
            shift 2
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

echo "🚀 [Findex] Preparing environment..."

# 1. Resolve Android SDK
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/home/aiyu/Android/Sdk" ]; then
        export ANDROID_HOME="/home/aiyu/Android/Sdk"
    fi
fi

if [ -n "$ANDROID_HOME" ]; then
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi

# Ensure adb is present
if ! command -v adb &> /dev/null; then
    echo "❌ Error: 'adb' not found. Please install Android platform-tools or set ANDROID_HOME."
    exit 1
fi

# 2. Check for running device or emulator
CONNECTED_DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | awk '{print $1}')
DEVICE_COUNT=$(echo "$CONNECTED_DEVICES" | sed '/^\s*$/d' | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "⚠️  No active Android device or emulator detected."
    
    # Check if emulator tool is available
    if ! command -v emulator &> /dev/null; then
        echo "❌ Error: 'emulator' binary not found. Cannot launch AVD automatically."
        echo "Please start an Android device or emulator and retry."
        exit 1
    fi

    # Pick AVD
    AVAILABLE_AVDS=$(emulator -list-avds)
    if [ -z "$AVAILABLE_AVDS" ]; then
        echo "❌ Error: No Android Virtual Devices (AVDs) found."
        echo "Create an AVD using 'avdmanager create avd ...' or install one."
        exit 1
    fi

    if [ -z "$TARGET_AVD" ]; then
        TARGET_AVD=$(echo "$AVAILABLE_AVDS" | head -n 1)
    fi

    echo "📱 Launching emulator AVD: '$TARGET_AVD' in background..."
    nohup emulator -avd "$TARGET_AVD" -netdelay none -netspeed full > /tmp/findex_emulator.log 2>&1 &
    EMULATOR_PID=$!
    echo "   Emulator started (PID: $EMULATOR_PID, log: /tmp/findex_emulator.log)"

    echo "⏳ Waiting for emulator to connect to adb..."
    adb wait-for-device

    echo "⏳ Waiting for Android system to finish booting..."
    BOOT_TIMEOUT=120
    ELAPSED=0
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
        ELAPSED=$((ELAPSED + 2))
        if [ "$ELAPSED" -ge "$BOOT_TIMEOUT" ]; then
            echo "❌ Timeout waiting for emulator boot completion (${BOOT_TIMEOUT}s)."
            exit 1
        fi
        echo "   Still booting... (${ELAPSED}s)"
    done
    echo "✅ Emulator is booted and ready!"
else
    ACTIVE_DEVICE=$(echo "$CONNECTED_DEVICES" | head -n 1)
    echo "✅ Connected device detected: $ACTIVE_DEVICE"
fi

# 3. Check for display overrides / reset if requested
OVERRIDE_SIZE=$(adb shell wm size 2>/dev/null | grep -i "Override" || true)
OVERRIDE_DENSITY=$(adb shell wm density 2>/dev/null | grep -i "Override" || true)

if [ "$RESET_DISPLAY" -eq 1 ] || [ -n "$OVERRIDE_SIZE" ] || [ -n "$OVERRIDE_DENSITY" ]; then
    if [ -n "$OVERRIDE_SIZE" ] || [ -n "$OVERRIDE_DENSITY" ]; then
        echo "⚠️  Detected display override ($OVERRIDE_SIZE $OVERRIDE_DENSITY)."
    fi
    echo "🖥️  Resetting display size and density to physical defaults..."
    adb shell wm size reset
    adb shell wm density reset
    echo "✅ Display restored: $(adb shell wm size | tr -d '\r') @ $(adb shell wm density | tr -d '\r')"
fi

# 4. Clean if requested
if [ "$DO_CLEAN" -eq 1 ]; then
    echo "🧹 Cleaning previous build artifacts..."
    ./gradlew clean
fi

# 5. Build and install debug APK
echo "📦 Building and installing Findex debug APK (:app:installDebug)..."
./gradlew :app:installDebug

# 6. Launch the MainActivity
echo "⚡ Launching $PACKAGE_NAME..."
adb shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

echo "🎉 Findex launched successfully on device!"

# 7. Stream logcat if requested
if [ "$LOGS" -eq 1 ]; then
    echo "📋 Streaming logs for $PACKAGE_NAME (Ctrl+C to stop)..."
    sleep 1
    APP_PID=$(adb shell pidof -s "$PACKAGE_NAME" 2>/dev/null || true)
    if [ -n "$APP_PID" ]; then
        adb logcat -v color --pid="$APP_PID"
    else
        adb logcat -v color -s "Findex:*" "AndroidRuntime:E" "*:E"
    fi
fi
