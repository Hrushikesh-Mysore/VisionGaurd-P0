# VisionGuard Status

## Current Phase
**Phase 0: Risk Spike (Ready for On-Device Verification)**

## What is Completed
- Scaffolding of Android project with Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.20, and Compose.
- Zero-cloud manifest enforcement: verified neither `INTERNET` nor `ACCESS_NETWORK_STATE` is present in the merged manifest (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`).
- Pure-Kotlin `SpikePolicy` with 100% JVM unit test coverage (`calculateWidthFraction`, `isTooClose`).
- `SpikeOverlayManager` providing non-focusable, non-touchable overlay (`TYPE_APPLICATION_OVERLAY` at 0.5 alpha).
- `CameraForegroundService` (`LifecycleService`, `foregroundServiceType="camera"`, persistent notification, `ACTION_SCREEN_OFF/ON` dynamic power-gating receiver, CameraX + ML Kit bundled face detection).
- Compose UI (`MainActivity`) with Start/Stop button, permission checklist with direct setting links, live detection metrics HUD, and manual overlay toggle.
- Debug APK assembled and successfully installed on physical device `ZF6526CJ97` (Motorola Moto E7 Plus, Android 10 / API 29).
- Camera and Overlay permissions pre-granted via ADB.

## Phase 0 Spike Verification Table

| Spike Row | Target Behavior | Status | Verification Method |
|---|---|---|---|
| 1. Camera service in background | Runs in background with ongoing notification | UNVERIFIED | Tap "Start Protection", switch to home screen, verify notification remains active |
| 2. Face detected with screen on | Face count and widthFraction update live | UNVERIFIED | Look at front camera; verify faces and widthFraction update in app and in logcat |
| 3. Overlay appears over other apps | Dim overlay appears over other apps when too close | UNVERIFIED | Open Chrome while protection is active, bring phone close to face (<30 cm), verify screen dims |
| 4. Overlay is click-through | Overlay allows full interaction with underlying app | UNVERIFIED | While dim overlay is active over Chrome, tap links and scroll; touches must pass through |
| 5. Camera stops when screen off | Camera analysis unbinds on screen off and rebinds on screen on | UNVERIFIED | Lock screen; verify analysis unbinds; unlock screen; verify analysis rebinds (check logcat) |

*Note: Per honesty rule, items requiring physical phone manipulation are marked UNVERIFIED until manually verified on device.*

## What is Currently Being Worked On
- Physical phone verification of Phase 0 spike table by user.

## What is Not Completed
- Phase 1: Eye Guard + Home screen.
- Phase 2: Privacy Guard.
- Phase 3: Smart Dashboard.
- Phase 4: Profiles + PIN.
- Phase 5: Time Tokens.
- Phase 6: Privacy Ledger.
- Phase 7: Polish and APK.
- Phase 8: Event pack.

## Known Bugs / Problems / Blockers
- Device screen is currently locked with a PIN; user must unlock phone to interact with `MainActivity`.

## Tests Performed and Results
- Unit tests (`./gradlew test`): **PASS** (SpikePolicyTest: 3/3 passed).
- Manifest check (`./gradlew :app:processDebugMainManifest`): **PASS** (Zero network permissions).
- APK Build (`./gradlew assembleDebug`): **PASS** (`app-debug.apk` generated, size 63MB with bundled ML Kit model).
- APK Deployment (`adb install -r`): **PASS** (Installed to `ZF6526CJ97`).

## Build Status
- **SUCCESS** (`./gradlew assembleDebug` and `./gradlew test` exit 0).

## APK / Device Testing Status
- Installed on device `ZF6526CJ97`. Awaiting user live confirmation of spike table rows.

## Exact Next Recommended Action
- User to unlock phone and execute the 5-step test script.
- Mark spike table rows as PASS/FAIL based on real device behavior.
- Tag `phase-0` and await user instruction (`next`) before starting Phase 1.

## Important Decisions Made
- Bundled ML Kit face detection (`com.google.mlkit:face-detection:16.1.7`) used instead of unbundled Play Services to ensure 100% offline functionality.
- Deprecated `LocalLifecycleOwner` replaced with `androidx.lifecycle.compose.LocalLifecycleOwner`.
- Overlay alpha set to 0.5 (safe touch pass-through margin well below Android 12+ limit of 0.8).
