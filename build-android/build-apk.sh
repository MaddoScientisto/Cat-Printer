#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
env_file="build-android/.env"

BUILD_MODE=${1:-${BUILD_MODE:-debug}}
export BUILD_MODE

cd "$repo_root"

env_args=""
if [ -f "$env_file" ]; then
    env_args="--env-file $env_file"
fi

if docker compose version >/dev/null 2>&1; then
    # shellcheck disable=SC2086
    docker compose $env_args -f docker-compose.android.yml up --build --abort-on-container-exit --exit-code-from android-build android-build
else
    # shellcheck disable=SC2086
    docker-compose $env_args -f docker-compose.android.yml up --build --abort-on-container-exit --exit-code-from android-build android-build
fi

