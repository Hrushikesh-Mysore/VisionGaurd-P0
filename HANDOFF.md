# VisionGuard Agent Handoff

## 1. Current Project State
Phase 1 (Eye Guard) has been completely implemented, verified via 15/15 JVM unit tests, checked against zero-network manifest requirements, built as a debug APK, and installed on the physical Motorola Moto E7 Plus (`ZF6526CJ97`, Android 10).

## 2. Last Completed Task
Phase 1 — Eye Guard:
- **Clean Face Observation**: `FaceObservation` data class mapping frame bounding box width, yaw, and face count.
- **Pinhole Distance Estimation & Policy**: `EyeGuardPolicy` with calibration ($K$), EMA smoothing ($\alpha = 0.35$), $N = 5$ consecutive-frame confirmation, hysteresis (~20 cm trigger, ~30 cm recovery), and proportional dimming (0.45..0.80).
- **Local Room Database (KSP)**: `AppDatabase`, `EyeGuardEventEntity`, `EyeGuardEventDao` storing derived safety events (`TOO_CLOSE`, `RECOVERED`, `CALIBRATION`, `PAUSED`, `RESUMED`, `NO_FACE_POWER_SAVING`) offline.
- **Calibration & Settings UI**: Added Calibration Card (~30 cm reference) and trigger threshold settings (20, 25, 30 cm) to `MainActivity.kt`.
- **Proportional Dimming**: `SpikeOverlayManager` updated to scale overlay opacity proportionally with proximity, strictly capped at 0.80 for touch pass-through.
- **User Warnings & Gating**: Prominent warning banner, rate-limited notification alert, persistent notification with Pause/Resume, 5s no-face 1 fps idle power saving, and screen-off unbinding preserved.
- **Unit Tests**: 15/15 unit tests passing across `EyeGuardPolicyTest` and `SpikePolicyTest`.

## 3. Current Blocker
Awaiting user manual on-device verification on the connected Motorola Moto E7 Plus.

## 4. Files Changed Recently
- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/java/com/visionguard/policy/Clock.kt`
- `app/src/main/java/com/visionguard/policy/FaceObservation.kt`
- `app/src/main/java/com/visionguard/policy/EyeGuardPolicy.kt`
- `app/src/main/java/com/visionguard/policy/SpikePolicy.kt`
- `app/src/main/java/com/visionguard/data/EyeGuardEventEntity.kt`
- `app/src/main/java/com/visionguard/data/EyeGuardEventDao.kt`
- `app/src/main/java/com/visionguard/data/AppDatabase.kt`
- `app/src/main/java/com/visionguard/overlay/SpikeOverlayManager.kt`
- `app/src/main/java/com/visionguard/AppContainer.kt`
- `app/src/main/java/com/visionguard/vision/CameraForegroundService.kt`
- `app/src/main/java/com/visionguard/ui/MainActivity.kt`
- `app/src/test/java/com/visionguard/policy/EyeGuardPolicyTest.kt`
- `STATUS.md`
- `CHANGELOG.md`
- `CODE_TOUR.md`
- `HANDOFF.md`

## 5. Tests / Build Results
- `./gradlew test`: **PASS** (15/15 unit tests passed).
- `./gradlew :app:processDebugMainManifest`: **PASS** (Zero network permissions confirmed).
- `./gradlew assembleDebug`: **PASS** (Debug APK built).
- `adb install -r`: **PASS** (Deployed to device `ZF6526CJ97`).

## 6. Exact Next Step
1. Prompt user to perform the manual verification script on device:
   - Start protection.
   - Calibrate at ~30 cm.
   - Test safe distance (~35–40 cm) -> no dim.
   - Move to ~20 cm -> warning banner appears, screen dims proportionally.
   - Back away to ~30–40 cm -> warning clears, dim clears within 1 second.
   - Test with eyes obscured -> bounding box triggers proximity dimming.
   - Test no-face for >5s -> screen dims, 1 fps idle mode.
   - Test face return -> dim clears, full rate resumes.
   - Test Pause/Resume from notification shade.
   - Verify events in Local Safety Event Ledger card.
2. Wait for user explicit command `next` or `start Phase 2`. Do NOT begin Phase 2 until instructed.

## 7. Important Warnings / Things NOT to Redo
- Do NOT begin Phase 2 until the user explicitly instructs.
- Do NOT introduce `INTERNET` or `ACCESS_NETWORK_STATE` into the manifest.
- Do NOT use DI frameworks (Hilt) or AccessibilityService.
