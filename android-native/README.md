# Cat Printer Native Android

This is a Kotlin Android frontend for Cat-Printer that does not embed Python, WebView, or the browser UI. It ports the BLE printer path used by `printer.py` into native Android code.

## What is included

- BLE scan and connect for supported model prefixes: GB01, GB02, GB03, GT01, YT01, MX05, MX06, MX08, MX09, MX10, MX11, SC03h, MXTP.
- Native command framing, CRC8, flow-control notification handling, and bitmap line writes.
- Native text printing via Android text rendering.
- Image, text, and pattern print modes with a mode selector that defaults to Image.
- Dynamic preview generated from the same 1bpp raster data that is sent to the printer.
- Native image picking, scaling to printer paper width, brightness, rotation, and flip controls.
- Dithering choices: No dithering, Floyd-Steinberg, and Bayer 4x4 with adjustable Bayer range.
- Automatic light/dark colors based on the Android system theme.
- External image sharing/view intents, with optional immediate print through the last connected printer.
- Long-press the preview to compare against the original source image.
- In-app error log for troubleshooting scan, connect, intent, and print failures.
- Dry-run mode, energy control, and the existing MX05/MX06/MX08/MX09/MX10 feed workaround.

## Build

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to:

```text
build/native-app/outputs/apk/debug/app-debug.apk
```

Release builds are available through the GitHub workflow in `.github/workflows/android-native-release.yml`. It publishes `cat-printer-native-release.apk` on matching tag builds and supports optional signing secrets. See `docs/external-image-intents.md` for signing and intent integration details.

## Install and launch with ADB

```powershell
adb install -r build/native-app/outputs/apk/debug/app-debug.apk
adb shell am start -n soft.naitlee.catprinter.nativeapp/.MainActivity
```

On Android 12 and newer the app requests `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`. On older Android versions it requests the legacy Bluetooth/location permissions required for BLE scanning.
