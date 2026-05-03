#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
apk=$(find "$script_dir/out" -maxdepth 1 -name '*.apk' -type f -print | sort | tail -n 1)
adb_bin=${ADB:-adb}

if ! command -v "$adb_bin" >/dev/null 2>&1; then
    if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        adb_bin="$ANDROID_HOME/platform-tools/adb"
    elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
        adb_bin="$ANDROID_SDK_ROOT/platform-tools/adb"
    fi
fi

if [ -z "$apk" ]; then
    echo "No APK found in $script_dir/out" >&2
    exit 1
fi

"$adb_bin" install -r "$apk"

