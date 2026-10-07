# VisionGuard Agent Handoff

## 1. Current Project State
Phase 0 (Risk Spike) has been completely implemented, tested, built, and installed on the physical test device `ZF6526CJ97` (Motorola Moto E7 Plus, Android 10). The APK is currently running on the phone. Build passes cleanly (`./gradlew assembleDebug` and `./gradlew test`), and the merged manifest has been verified to contain zero network permissions.

## 2. Last Completed Task
Phase 0 implementation and deployment:
- Camera foreground service (`LifecycleService`, `foregroundServiceType="camera"`, persistent notification, `ACTION_SCREEN_OFF/ON` dynamic power-gating).
- CameraX front-camera analysis + bundled ML Kit face detection (`PERFORMANCE_MODE_FAST`).
- Non-intrusive click-through `TYPE_APPLICATION_OVERLAY` (alpha 0.5, `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`).
- Compose UI with Start/Stop toggle, permission checklist, live detection HUD, and manual overlay toggle.
- Unit tests for pure-Kotlin `SpikePolicy`.
- Debug APK built and installed via ADB.

## 3. Current Blocker
Awaiting user manual execution of the 5 on-device spike verification tests (recorded as `UNVERIFIED` in `STATUS.md`).

## 4. Files Changed Recently
- `app/build.gradle.kts`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/visionguard/VisionGuardApp.kt`
- `app/src/main/java/com/visionguard/AppContainer.kt`
- `app/src/main/java/com/visionguard/policy/SpikePolicy.kt`
- `app/src/test/java/com/visionguard/policy/SpikePolicyTest.kt`
- `app/src/main/java/com/visionguard/overlay/SpikeOverlayManager.kt`
- `app/src/main/java/com/visionguard/vision/CameraForegroundService.kt`
- `app/src/main/java/com/visionguard/ui/MainActivity.kt`
- `STATUS.md`
- `CHANGELOG.md`
- `CODE_TOUR.md`
- `HANDOFF.md`

## 5. Tests / Build Results
- `./gradlew test`: **PASS** (3/3 unit tests passed).
- `./gradlew :app:processDebugMainManifest`: **PASS** (INTERNET and ACCESS_NETWORK_STATE confirmed absent).
- `./gradlew assembleDebug`: **PASS** (`app-debug.apk` built successfully, 63 MB).
- `adb install -r`: **SUCCESS** on device `ZF6526CJ97`.

## 6. Exact Next Step
1. Prompt user to unlock physical phone and test the 5 spike table rows in `STATUS.md`.
2. Update `STATUS.md` with PASS/FAIL based on user feedback.
3. Commit completed Phase 0 work and tag `phase-0`.
4. Wait for user command `next` or `start Phase 1`. Do NOT start Phase 1 until explicitly commanded.

## 7. Important Warnings / Things NOT to Redo
- Do NOT rewrite or re-extract `AGENTS.md` or `docs/SRS.txt`.
- Do NOT add `INTERNET` or `ACCESS_NETWORK_STATE` to the Android manifest under any circumstance.
- Do NOT add Hilt or other DI libraries; continue using `AppContainer`.
- Do NOT use AccessibilityService or deprecated `security-crypto`.
- Always close `ImageProxy` in `finally` or inside `.addOnCompleteListener`.
- Maintain overlay opacity $\le 0.8$ to preserve touch pass-through.
