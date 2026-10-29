# Wi-Fi Signal Tracker (Android / Kotlin)

A native Android starter app with a premium dark dashboard, live Wi-Fi RSSI sampling, a signal history chart, and locally saved session reports.

## Features
- Live RSSI readings every 2 seconds while tracking is active
- Signal quality labels and visual meter
- Connected SSID, frequency, and reported Wi-Fi link speed
- In-session signal chart (last 120 samples)
- Stop-and-save reports with average/minimum/maximum RSSI and sample count
- Local report storage on the device; no account or server required

## Build
1. Open this folder in Android Studio (recent version with JDK 17).
2. Allow Gradle to sync and download Android Gradle Plugin / Compose dependencies.
3. Select an Android device or emulator and press Run.
4. Grant Wi-Fi/location-related permissions when prompted.

## Notes and limitations
- Android restricts SSID and scan information depending on OS version, permission state, location settings, and device vendor.
- RSSI is read from the currently connected Wi-Fi network; it is not a GPS or person-tracking tool.
- This starter samples while the app is open and tracking is active. It does not promise background tracking after the app is closed.
- Reports are stored locally in app preferences and disappear if app data is cleared or the app is uninstalled.
- Android may return an unknown SSID or stale readings in some configurations. Test on a physical device for best results.
- For reliable background monitoring, add a foreground service with a persistent notification and review current Android background execution and permission requirements.

## Build an APK automatically with GitHub Actions
1. Create a new GitHub repository and upload this project folder's contents.
2. Commit the `.github/workflows/android.yml` workflow to the `main` branch.
3. Open the repository's **Actions** tab and open **Build Android APK**.
4. After the workflow succeeds, open its run and download the `wifi-signal-tracker-debug-apk` artifact.
5. Extract the artifact ZIP to get `app-debug.apk`. Transfer it to your Android phone and install it. You may need to allow installs from your file manager/browser.

This workflow produces a debug APK for testing, not a Play Store release. A release APK should be signed with a private signing key.
