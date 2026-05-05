# External Image Intents

Cat Printer Native can receive images from Android's share sheet or from explicit intents sent by another app. Incoming images are loaded into Image mode and rendered through the same preview and print path as images picked inside the app.

## Supported intents

### Android share sheet

Use Android's standard share flow with an image MIME type:

- Action: `android.intent.action.SEND`
- MIME type: `image/*`, `image/png`, or `image/jpeg`
- Extra stream: `android.intent.extra.STREAM`
- URI permission: grant read permission for the content URI

The receiving activity is:

```text
soft.naitlee.catprinter.nativeapp/.MainActivity
```

Shared images open in the app for preview and manual printing. The last selected printer stays selected in the printer list so it is ready to connect.

### Direct view intent

Apps can also open an image URI directly:

- Action: `android.intent.action.VIEW`
- MIME type: `image/*`, `image/png`, or `image/jpeg`
- Data: a readable `content://` image URI
- URI permission: grant read permission

## ADB examples

ADB can only test this easily when you have a readable content URI. Replace the URI below with one from a media provider or a test app FileProvider:

```powershell
adb shell am start `
  -n soft.naitlee.catprinter.nativeapp/.MainActivity `
  -a android.intent.action.SEND `
  -t image/png `
  --eu android.intent.extra.STREAM "content://media/external/images/media/123" `
  --grant-read-uri-permission
```

For a direct view intent:

```powershell
adb shell am start `
  -n soft.naitlee.catprinter.nativeapp/.MainActivity `
  -a android.intent.action.VIEW `
  -d "content://media/external/images/media/123" `
  -t image/png `
  --grant-read-uri-permission
```

## Tasker example

Tasker can send an image to Cat Printer Native with a `Send Intent` action.

Recommended setup:

1. Create or choose a variable containing a content URI for the image, for example `%image_uri`.
2. Add an action: `System` -> `Send Intent`.
3. Set `Action` to `android.intent.action.SEND`.
4. Set `Cat` to `Default`.
5. Set `Mime Type` to `image/png`, `image/jpeg`, or `image/*`.
6. Set `Package` to `soft.naitlee.catprinter.nativeapp`.
7. Set `Class` to `soft.naitlee.catprinter.nativeapp.MainActivity`.
8. Add extra `android.intent.extra.STREAM:%image_uri` and mark it as a URI/file extra if your Tasker version exposes that option.
9. Add flag `Grant Read URI Permission` if available.
If your Tasker flow starts with a normal file path instead of a `content://` URI, use Tasker's file/share action or a FileProvider-capable plugin to convert it to a URI that grants read permission. Android apps generally cannot read another app's private file path directly.

## GitHub release builds

The workflow at `.github/workflows/android-native-release.yml` builds the native Android release APK.

It runs on manual dispatch and on tags matching `android-native-v*` or `v*`. Tag builds publish `cat-printer-native-release.apk` to the GitHub Release.

Signing secrets are optional. Without signing secrets, the workflow signs the release APK with the standard Android debug keystore so the package remains installable. To sign releases with your own key instead, add these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded JKS or PKCS12 keystore
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: signing key alias
- `ANDROID_KEY_PASSWORD`: signing key password

Example keystore encoding on PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```
