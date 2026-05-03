#!/usr/bin/env bash
set -euo pipefail

workspace=/workspace
android_dir=$workspace/build-android
output_dir=$android_dir/out
run_dir=/tmp/cat-printer-p4a-run
private_dir=/tmp/cat-printer-private

cd "$workspace"
git config --global --add safe.directory "$workspace" >/dev/null 2>&1 || true

mkdir -p "$output_dir"

if [ ! -f "$ANDROIDNDK/.cat-printer-ndk-fixed" ]; then
    python3 "$android_dir/fix-ndk-execs.py" "$ANDROIDNDK"
    touch "$ANDROIDNDK/.cat-printer-ndk-fixed"
fi

python3 "$android_dir/docker-prepare.py"

rm -rf "$private_dir"
mkdir -p "$private_dir"
tar -C "$workspace" \
    --exclude='./.git' \
    --exclude='./.idea' \
    --exclude='./build-android' \
    --exclude='./build-common' \
    --exclude='./cat-printer*.zip' \
    --exclude='./*.apk' \
    --exclude='./*.apk.*' \
    -cf - . | tar -C "$private_dir" -xf -

if [ "${CLEAN_BUILD:-0}" = "1" ]; then
    mkdir -p "$run_dir"
    (cd "$run_dir" && p4a clean_builds && p4a clean_dists)
fi

common_args=(
    --private "$private_dir"
    --dist_name "cat-printer"
    --package "${P4A_PACKAGE:-io.github.naitlee.catprinter}"
    --name "${P4A_NAME:-Cat Printer}"
    --icon "$android_dir/icon.png"
    --bootstrap webview
    --window
    --blacklist-requirements sqlite3,openssl
    --port 8095
    --blacklist "$android_dir/blacklist.txt"
    --presplash "$android_dir/blank.png"
    --presplash-color black
    --manifest-orientation user
    --android_api "${ANDROIDAPI:-35}"
    --ndk-api "${NDKAPI:-23}"
)

IFS=',' read -ra archs <<< "${P4A_ARCHS:-arm64-v8a,armeabi-v7a}"
for arch in "${archs[@]}"; do
    arch=$(echo "$arch" | xargs)
    if [ -n "$arch" ]; then
        common_args+=(--arch "$arch")
    fi
done

IFS=',' read -ra permissions <<< "${P4A_PERMISSIONS:-INTERNET,BLUETOOTH,BLUETOOTH_ADMIN,ACCESS_FINE_LOCATION,BLUETOOTH_SCAN,BLUETOOTH_CONNECT}"
for permission in "${permissions[@]}"; do
    permission=$(echo "$permission" | xargs)
    if [ -n "$permission" ]; then
        common_args+=(--permission "$permission")
    fi
done

if [ -n "${P4A_EXTRA_ARGS:-}" ]; then
    read -ra extra_args <<< "${P4A_EXTRA_ARGS}"
    common_args+=("${extra_args[@]}")
fi

build_mode=${BUILD_MODE:-debug}
version=$(tr -d '\r\n' < "$workspace/version")
requirements=$(tr -d '\r\n[:space:]' < "$android_dir/build-deps.txt")

rm -rf "$run_dir"
mkdir -p "$run_dir"
cd "$run_dir"

case "$build_mode" in
    debug)
        p4a apk --requirements "$requirements" --version "$version" "${common_args[@]}"
        ;;
    release)
        if [ ! -f "$workspace/cat-printer-bare-$version.zip" ]; then
            (cd "$workspace/build-common" && sh ./0-bundle-all.sh)
        fi

        release_args=("${common_args[@]}")
        if [ -n "${RELEASE_KEYSTORE:-}" ]; then
            release_args+=(--keystore "$RELEASE_KEYSTORE")
        fi
        if [ -n "${RELEASE_SIGNKEY:-}" ]; then
            release_args+=(--signkey "$RELEASE_SIGNKEY")
        fi
        if [ -n "${RELEASE_KEYSTORE_PASSWORD:-}" ]; then
            release_args+=(--keystorepw "$RELEASE_KEYSTORE_PASSWORD")
        fi
        if [ -n "${RELEASE_SIGNKEY_PASSWORD:-}" ]; then
            release_args+=(--signkeypw "$RELEASE_SIGNKEY_PASSWORD")
        fi

        rm -rf dist
        unzip -q "$workspace/cat-printer-bare-$version.zip"
        mv cat-printer dist
        p4a apk --version "$version" --requirements "$requirements" --release "${release_args[@]}"
        ;;
    *)
        echo "Unknown BUILD_MODE '$build_mode'. Use 'debug' or 'release'." >&2
        exit 2
        ;;
esac

shopt -s nullglob
apk_files=("$run_dir"/*.apk "$android_dir"/*.apk "$workspace"/*.apk)

if [ ${#apk_files[@]} -eq 0 ]; then
    echo "Build finished, but no APK was found in $android_dir or $workspace." >&2
    exit 1
fi

cp -f "${apk_files[@]}" "$output_dir/"
echo "APK output: $output_dir"
ls -lh "$output_dir"/*.apk

