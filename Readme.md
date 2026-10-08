# Running VisionGuard

## Prerequisites

Make sure you have:

* Android Studio installed
* JDK installed
* Android SDK installed
* A physical Android device with USB debugging enabled **or** an Android emulator
* Git installed

## 1. Clone the repository

```bash
git clone https://github.com/Hrushikesh-Mysore/VisionGaurd-P0.git
cd VisionGaurd-P0
```

## 2. Check the project

Make sure the Gradle wrapper is executable:

```bash
chmod +x gradlew
```

## 3. Build the app

Run:

```bash
./gradlew assembleDebug
```

If the build succeeds, the debug APK will be generated under:

```text
app/build/outputs/apk/debug/
```

## 4. Run on an Android device

Connect your Android phone using USB and make sure **USB debugging** is enabled.

Check that ADB detects the device:

```bash
adb devices
```

You should see your device listed.

Then install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch VisionGuard from the Android app launcher.

### Alternatively, install and launch directly with Gradle

If a connected Android device or emulator is available:

```bash
./gradlew installDebug
```

Then open **VisionGuard** from the device.

## 5. Run on an emulator

Open Android Studio and start an Android Virtual Device (AVD).

Then verify that it is detected:

```bash
adb devices
```

Install the application:

```bash
./gradlew installDebug
```

Launch **VisionGuard** from the emulator.

## 6. Running tests

To run the project's JVM tests:

```bash
./gradlew test
```

To perform a complete debug build and test verification:

```bash
./gradlew assembleDebug test :app:processDebugMainManifest --rerun-tasks
```

## Troubleshooting

### Device is not detected

Run:

```bash
adb devices
```

If the device is shown as `unauthorized`, unlock the phone and accept the **USB debugging** authorization prompt.

If ADB is not responding, restart it:

```bash
adb kill-server
adb start-server
adb devices
```

### Gradle permission denied

Run:

```bash
chmod +x gradlew
```

Then retry:

```bash
./gradlew assembleDebug
```

### Clean and rebuild

If the project has stale build files:

```bash
./gradlew clean
./gradlew assembleDebug
```

## Quick Start

For an already-configured development machine:

```bash
git clone https://github.com/Hrushikesh-Mysore/VisionGaurd-P0.git
cd VisionGaurd-P0
chmod +x gradlew
./gradlew installDebug
```

Then open **VisionGuard** on the connected Android device or emulator.
