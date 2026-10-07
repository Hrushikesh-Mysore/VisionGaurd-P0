# VisionGuard Status

## Current Phase
**Phase 0: Risk Spike (Final Proximity & No-Face Power Saving Implemented)**

## What is Completed
- **Project Scaffolding**: Single `:app` module with Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.20, and Jetpack Compose Material 3.
- **Zero-Cloud Architecture**: Strictly zero network permissions in merged manifest (`INTERNET` and `ACCESS_NETWORK_STATE` confirmed absent via `./gradlew :app:processDebugMainManifest`).
- **Feature Outcome 1: Proximity Detection (~20 cm Trigger, ~30 cm Recovery)**:
  - Stateful `ProtectionPolicy` with hysteresis: trigger threshold ~20 cm (`widthFraction >= 0.60`), recovery threshold ~30 cm (`widthFraction <= 0.45`).
  - Distance estimation model ($d \approx K / \text{widthFraction}$, $K \approx 12.0$).
  - Proximity relies strictly on overall face bounding box width; does not require eye landmarks or classifications.
- **Feature Outcome 2: Dedicated No-Face Battery Saving (>5s Timeout + Screen Dim)**:
  - Explicit distinction maintained: `NO FACE != TOO CLOSE`. Zero faces is never falsely classified as proximity violation.
  - When no face is detected for $> 5$ seconds continuously, system transitions from `NO_FACE_GRACE_PERIOD` to `NO_FACE_DIMMED`.
  - In `NO_FACE_DIMMED`, the dim overlay is displayed and camera frame analysis is throttled to 1 fps idle polling to conserve CPU/GPU battery.
  - When a face returns: system exits `NO_FACE_DIMMED`, restores full-rate analysis, and clears dimming unless the returning face is within the ~20 cm proximity threshold.
- **Feature Outcome 3: Notification Pause / Resume**:
  - Interactive "Pause Protection" and "Resume Protection" notification actions implemented via `PendingIntent`.
  - Pausing unbinds the camera hardware, dismisses the dim overlay, and updates the ongoing notification title to "VisionGuard (Paused)".
  - Resuming re-binds CameraX and resets policy timers without restarting the service.
- **Screen-Off Lifecycle Gating**:
  - Camera analysis unbinds completely on `ACTION_SCREEN_OFF` and automatically rebinds on `ACTION_SCREEN_ON`.
- **System Overlay**:
  - Non-focusable, non-touchable overlay view using `TYPE_APPLICATION_OVERLAY` at 0.5 alpha, guaranteeing touch pass-through.
- **Testing & Deployment**:
  - JVM unit tests (`SpikePolicyTest`): 8/8 passed.
  - Manifest privacy audit: PASS (0 network permissions).
  - Debug APK built and installed on connected Motorola Moto E7 Plus (`ZF6526CJ97`, Android 10).

## Phase 0 Spike & Fix Verification Table

| Spike Item | Target Behavior | Status | Verification Method |
|---|---|---|---|
| 1. Camera service background | Runs in background with ongoing notification | PASS | Tested on device in initial run |
| 2. Face detection active | Live face count and widthFraction update in app and logcat | PASS | Confirmed in initial run (`faces=1, widthFraction=0.369`) |
| 3. ~20 cm Proximity Trigger | Proximity dim triggers at ~20 cm ($\ge 0.60$) and clears at ~30 cm ($\le 0.45$) | UNVERIFIED | Unit tests pass (8/8); manual distance test on phone required |
| 4. Eye-independent detection | Bounding box triggers even if eyes are obscured/not visible; no-face clears proximity dim | UNVERIFIED | Cover eyes or move close; verify face width still triggers; step out of frame; verify proximity dim clears |
| 5. No-face >5s battery-saving state | Screen dims and analysis throttles to 1 fps after 5s continuous no-face | UNVERIFIED | Move phone away from face for 5s; check UI "Power Saving" indicator and logcat |
| 6. Face return after no-face | Restores full analysis rate; clears dim unless face is too close | UNVERIFIED | Look back at phone after >5s away; check dim removal and analysis rate resumption |
| 7. Notification Pause / Resume | Action button in notification pauses/resumes dimming and camera binding | UNVERIFIED | Pull notification shade, tap "Pause Protection"; verify title updates to "(Paused)"; tap "Resume" |
| 8. Overlay click-through | Overlay allows full touch interaction with underlying apps | PASS | Tested with Chrome in initial run |
| 9. Screen off/on gating | Camera unbinds on screen off and rebinds on screen on | PASS | Tested via ACTION_SCREEN_OFF/ON logcat in initial run |

*Note: Per honesty rule, items modified in this fix are marked UNVERIFIED until manually verified by user on device.*

## What is Currently Being Worked On
- Physical phone verification of Phase 0 behavioral fixes by the user.

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
- None blocking; awaiting user testing on physical phone (`ZF6526CJ97`).

## Tests Performed and Results
- Unit tests (`./gradlew test`): **PASS** (8/8 tests in `SpikePolicyTest`).
- Manifest privacy check (`./gradlew :app:processDebugMainManifest`): **PASS** (zero network permissions).
- APK Build (`./gradlew assembleDebug`): **PASS**.
- APK Deployment (`adb install -r`): **PASS** (installed to `ZF6526CJ97`).

## Build Status
- **SUCCESS** (`./gradlew assembleDebug` and `./gradlew test` exit 0).

## APK / Device Testing Status
- Final Phase 0 APK installed on `ZF6526CJ97`. Awaiting user verification script on device.

## Exact Next Recommended Action
- User to test the installed APK on phone:
  1. Face present at safe distance (~35 cm) -> No dim.
  2. Face brought close (~20 cm) -> Screen dims.
  3. Face close with eyes covered -> Screen still dims if bounding box detected.
  4. Phone facing away / no face for >5s -> Screen dims (battery saving, 1 fps).
  5. Face returns -> Screen un-dims (unless face is close).
  6. Notification: Tap "Pause Protection" -> Dimming pauses, camera unbinds. Tap "Resume Protection" -> Resumes.
  7. Turn screen off -> Camera unbinds. Turn screen on -> Camera resumes.
- Await user command (`next` / `start Phase 1`) before Phase 1.

## Important Decisions & Honest Limitations
- **Approximate Distance**: Distance is approximate ($d \approx 12 / \text{widthFraction}$, ~15% variance depending on face geometry).
- **Unmeasured Battery**: 1 fps idle poll avoids >95% of ML Kit inferences when nobody is looking, but overall battery savings are not claimed as measured until physical multi-hour battery benchmarking.
- **Hysteresis**: Trigger at 0.60, recovery at 0.45 prevents rapid screen dim flickering.
- **Offline ML Kit**: Configured with `LANDMARK_MODE_NONE` and `CLASSIFICATION_MODE_NONE` for minimal CPU usage.
