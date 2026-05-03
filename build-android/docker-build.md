# Docker Android Build

This build path wraps the manual Android setup in Docker Compose. It installs the Android SDK, NDK, Java, `python-for-android`, the Bleak Android recipe, and the AdvancedWebView bootstrap changes required by Cat Printer.

## Requirements

- Docker with Compose support (`docker compose`) or the legacy `docker-compose` command.
- Enough free disk space for the Android SDK, Gradle cache, and p4a build cache.

## Quick start

From the repository root on Linux/macOS/Git Bash:

```sh
./build-android/build-apk.sh
```

From Windows Command Prompt or PowerShell:

```bat
build-android\build-apk.bat
```

The default build mode is `debug`. APK files are copied to:

```text
build-android/out/
```

The Docker debug build has been verified to produce `build-android/out/cat-printer-debug-0.6.3.0.apk` in this workspace.

## Configuration

Copy the example environment file when defaults need to be changed:

```sh
cp build-android/.env.example build-android/.env
```

On Windows PowerShell:

```powershell
Copy-Item build-android\.env.example build-android\.env
```

`build-android/.env` is intentionally ignored by Git because it can contain local paths and signing secrets.

Important defaults:

- `ANDROID_API=35` targets Android 15 for current Android systems.
- `NDK_VERSION=27.2.12479018` uses NDK r27c, which supports Android API 35.
- `NDK_API=23` keeps compatibility with Android 6.0 and newer devices.
- `P4A_ARCHS=arm64-v8a,armeabi-v7a` covers current 64-bit devices and older 32-bit devices.
- `P4A_PERMISSIONS` includes Bluetooth permissions needed by recent Android versions.

## Build modes

Debug build:

```sh
./build-android/build-apk.sh debug
```

Release build:

```sh
./build-android/build-apk.sh release
```

Windows equivalents:

```bat
build-android\build-apk.bat debug
build-android\build-apk.bat release
```

For release signing, place the keystore somewhere mounted into the container, for example `build-android/keys/mykeyfile.key`, then set these values in `build-android/.env`:

```text
RELEASE_KEYSTORE=/workspace/build-android/keys/mykeyfile.key
RELEASE_SIGNKEY=mykey
```

Add password variables only if your `python-for-android` signing flow requires them.

## Clean rebuilds

Set this in `build-android/.env` to force `python-for-android` to clean its builds and dists before building:

```text
CLEAN_BUILD=1
```

Docker named volumes keep the p4a, Gradle, and pip caches between builds. If the Android toolchain itself changes, rebuild the image with the launcher or run Docker Compose with `--build`.

## Android Studio / JetBrains setup

This is a `python-for-android` WebView app, not a native Gradle Android project. Use Android Studio or IntelliJ as a script runner around the Docker build instead of importing it as an Android Gradle module.

Shared run configurations are provided in `.idea/runConfigurations`:

- `Android Docker Debug Build` runs `build-android/build-apk.bat debug`.
- `Android Install Built APK` runs `build-android/install-built-apk.bat` and installs the newest APK from `build-android/out` with `adb install -r`.
- `Android Logcat App` runs `build-android/logcat-app.bat` with filters for Python, Chromium/WebView, the app package, and Android runtime crashes.

Recommended editor workflow:

1. Open the repository folder in Android Studio.
2. Make sure Docker Desktop is running.
3. Install Android SDK Platform Tools locally. The helper scripts check `PATH`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and the default Windows Android Studio SDK path for `adb`.
4. Run `Android Docker Debug Build`.
5. Connect a device or start an emulator with USB debugging enabled.
6. Run `Android Install Built APK`.
7. Run `Android Logcat App` while testing the app.

If the Shell Script run configuration type is unavailable, enable the JetBrains Shell Script plugin or create equivalent run configurations that execute the same `.bat` files from the project root.

## What the container automates

The container performs the manual steps that used to be easy to miss:

- Installs a modern Android SDK platform, build-tools, and NDK.
- Runs the repository's NDK executable fix once per container.
- Adds the older Bleak p4android recipe required by this project.
- Copies the vendored AdvancedWebView Java source into the p4a webview bootstrap.
- Patches p4a `PythonActivity.java` so file inputs work in the WebView.
- Copies Cat Printer loading assets into the webview bootstrap.

The original manual guide remains available in `manual-steps.md` for troubleshooting or non-Docker builds.
