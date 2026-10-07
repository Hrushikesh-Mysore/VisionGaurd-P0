# VisionGuard Status

## Current Phase
**Phase 0: Risk Spike (Behavioral Fixes Applied - Awaiting Device Retest)**

## What is Completed
- **Project Scaffolding**: Single `:app` module with Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.20, and Compose Material 3.
- **Zero-Cloud Architecture**: Verified zero network permissions in merged manifest (`INTERNET` and `ACCESS_NETWORK_STATE` strictly absent).
- **Fix 1 (20 cm Threshold & Hysteresis)**:
  - Trigger threshold tuned to approximately 20 cm (`widthFraction >= 0.60`).
  - Recovery threshold tuned to approximately 30 cm (`widthFraction <= 0.45`).
  - Implemented stateful `ProximityEstimator` in pure Kotlin with hysteresis preventing screen dim flickering between 0.45 and 0.60.
  - Documented distance formula ($d \approx K / \text{widthFraction}$, $K \approx 12.0$) as an approximation.
- **Fix 2 (Face Geometry Proximity, No Eyes Required)**:
  - Face detection relies strictly on overall face bounding box width, completely decoupled from eye visibility, landmarks, or classifications.
  - Explicit distinction between `NO_FACE_DETECTED` (dim overlay immediately dismissed) and `TOO_CLOSE` / `NORMAL_DISTANCE`.
- **Fix 3 (Battery Power Saving for No-User State)**:
  - When no face is detected continuously for $> 5$ seconds while screen is on, frame analysis is throttled from full capture rate to 1 frame per second (1 fps idle poll), reducing ML Kit CPU/GPU processing by over 95%.
  - When a face is detected again during the 1 fps poll, analysis immediately resumes at full rate.
- **Fix 4 (Persistent Notification Pause / Resume)**:
  - Added interactive `Pause Protection` / `Resume Protection` action buttons directly to the ongoing foreground notification.
  - Allows user to temporarily suspend proximity dimming without terminating the background service or opening the app.
  - Mirrored pause/resume state dynamically across the notification title, description, and the in-app Compose UI.
- **Testing & Deployment**:
  - JVM unit tests (`SpikePolicyTest`): 5/5 passed.
  - Merged manifest privacy audit: passed (0 network permissions).
  - Debug APK built and installed on connected Motorola Moto E7 Plus (`ZF6526CJ97`, Android 10).

## Phase 0 Spike & Fix Verification Table

| Spike Item | Target Behavior | Status | Verification Method |
|---|---|---|---|
| 1. Camera service background | Runs in background with ongoing notification | PASS | Tested on device in initial run |
| 2. Face detection active | Live face count and widthFraction update in app and logcat | PASS | Confirmed in initial run (`faces=1, widthFraction=0.369`) |
| 3. 20 cm Trigger / 30 cm Recovery | Too-close warning + dim triggers at ~20 cm ($\ge 0.60$) and clears at ~30 cm ($\le 0.45$) | UNVERIFIED | Unit tests pass (5/5); manual distance test on phone required |
| 4. Eye-independent detection | Bounding box triggers even if eyes are obscured/not visible; no-face clears dim | UNVERIFIED | Cover eyes or move close; verify face width still triggers; step out of frame; verify dim clears |
| 5. Battery power saving (>5s no face) | Throttles to 1 fps when no face seen for 5s; resumes instantly on face return | UNVERIFIED | Move phone away from face for 5s; check UI "Power Saving" indicator and logcat |
| 6. Notification Pause / Resume | Action button in notification pauses/resumes dimming without killing service | UNVERIFIED | Pull notification shade, tap "Pause Protection"; verify title updates to "(Paused)"; tap "Resume" |
| 7. Overlay click-through | Overlay allows full touch interaction with underlying apps | PASS | Tested with Chrome in initial run |
| 8. Screen off/on gating | Camera unbinds on screen off and rebinds on screen on | PASS | Tested via ACTION_SCREEN_OFF/ON logcat in initial run |

*Note: Per honesty rule, items modified in this fix are marked UNVERIFIED until manually verified by user on device.*

## What is Currently Being Worked On
- On-device manual verification of Phase 0 behavioral fixes by the user.

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
- None currently blocking; awaiting physical phone retest of updated thresholds and notification controls.

## Tests Performed and Results
- Unit tests (`./gradlew test`): **PASS** (5/5 tests in `SpikePolicyTest`).
- Manifest privacy check (`./gradlew :app:processDebugMainManifest`): **PASS** (zero network permissions).
- APK Build (`./gradlew assembleDebug`): **PASS**.
- APK Deployment (`adb install -r`): **PASS** (installed to `ZF6526CJ97`).

## Build Status
- **SUCCESS** (`./gradlew assembleDebug` and `./gradlew test` exit 0).

## APK / Device Testing Status
- Updated APK installed on `ZF6526CJ97`. Awaiting user verification of fixes 1–4.

## Exact Next Recommended Action
- User to test the updated APK on device (20 cm distance, eye occlusion, 5s no-face throttling, and notification Pause action).
- Report PASS/FAIL for updated rows.
- Await user command (`next`) before beginning Phase 1.

## Important Decisions Made
- `ProximityEstimator` uses hysteresis (trigger: 0.60, recovery: 0.45) to prevent flicker.
- ML Kit configured with `LANDMARK_MODE_NONE` and `CLASSIFICATION_MODE_NONE` for fast face-bounding-box-only detection.
- Battery conservation operates by frame-level throttling (1 fps idle poll) rather than unbinding/rebinding camera hardware, eliminating camera HAL re-initialization lag.
- Notification pause keeps the foreground service alive with `PendingIntent` actions (`FLAG_IMMUTABLE`).
