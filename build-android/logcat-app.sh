#!/usr/bin/env sh
set -eu

adb_bin=${ADB:-adb}

if ! command -v "$adb_bin" >/dev/null 2>&1; then
	if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
		adb_bin="$ANDROID_HOME/platform-tools/adb"
	elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
		adb_bin="$ANDROID_SDK_ROOT/platform-tools/adb"
	fi
fi

"$adb_bin" logcat | grep -E 'python|chromium|io\.github\.naitlee\.catprinter|AndroidRuntime'

