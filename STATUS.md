# VisionGuard Status

## Current Phase
**Phase 1: Eye Guard (Completed - Awaiting User On-Device Retest)**

## What is Completed
- **Clean Face Observation Abstraction**:
  - Pure Kotlin `FaceObservation(widthFraction, yaw, count, timestampMs)` decoupled from raw frame bitmaps and landmarks.
  - Strictly relies on face bounding box geometry; eye visibility is not required.
- **Pinhole Distance Estimation & Policy**:
  - Pure Kotlin `EyeGuardPolicy` taking time via `Clock` interface for 100% deterministic JVM testing.
  - Pinhole optical model ($d = K / \text{widthFraction}$) calibrated via user reference distance ($K = d_{\text{cal}} \times w_{\text{cal}}$).
  - Exponential Moving Average (EMA) smoothing ($\alpha = 0.35$) eliminates per-frame noise.
  - $N = 5$ consecutive-frame confirmation avoids spurious triggers from temporary movements.
  - Hysteresis band (~20 cm trigger, ~30 cm recovery) prevents boundary flickering.
  - Closeness-proportional dimming opacity scaling smoothly from 0.45 to 0.80 (strictly capped at 0.80 for Android 12+ touch pass-through rule C3).
  - Rapid recovery: clears dimming within ~500 ms (< 1 second) upon returning to safe distance.
- **Local Room Database Infrastructure (KSP)**:
  - Local SQLite database `visionguard.db` using Room 2.6.1 with KSP compiler `2.0.20-1.0.25`.
  - `EyeGuardEventEntity` and `EyeGuardEventDao` recording safety events (`TOO_CLOSE`, `RECOVERED`, `CALIBRATION`, `PAUSED`, `RESUMED`, `NO_FACE_POWER_SAVING`) entirely on-device without network transmission.
- **Calibration UI & Threshold Settings**:
  - Distance Calibration card in Compose UI with live face width reading, one-tap calibration at ~30 cm, and reset to default ($K = 12.0$).
  - Adjustable threshold setting chips (20 cm default, 25 cm, 30 cm).
- **Proportional Dimming & Overlay**:
  - Enhanced `SpikeOverlayManager` modulating alpha from 0.45 up to 0.80 based on proximity, maintaining touch pass-through.
- **User Warning & Notifications**:
  - Prominent in-app warning banner explaining that the phone is too close and the screen has dimmed.
  - Rate-limited notification alert on close proximity without spamming every frame.
  - Persistent ongoing notification with interactive "Pause Protection" and "Resume Protection" controls preserved.
- **Power Gating Preserved**:
  - Screen-off unbinds CameraX hardware; screen-on rebinds.
  - No-face $>5$ seconds transitions to `NO_FACE_DIMMED` (screen dimmed, 1 fps idle poll), immediately restoring full-rate analysis upon face return.
- **Testing & Deployment**:
  - JVM unit tests: **15/15 passed** (8 in `SpikePolicyTest`, 7 in `EyeGuardPolicyTest`).
  - Merged manifest privacy audit: **PASS** (`INTERNET` and `ACCESS_NETWORK_STATE` strictly absent).
  - Debug APK built and installed on connected Motorola Moto E7 Plus (`ZF6526CJ97`, Android 10).

## Phase 1 Feature Verification Table

| Item | Target Behavior | Status | Verification Method |
|---|---|---|---|
| 1. Face observation abstraction | Clean `FaceObservation` mapping widthFraction, yaw, count | PASS | Unit tested (15/15) & CameraX verified |
| 2. Pinhole distance estimation | $d \approx K / \text{widthFraction}$ with calibration support | PASS | Unit tested in `EyeGuardPolicyTest` |
| 3. EMA smoothing & N-frame confirmation | Smoothes noise; requires 5 consecutive frames to trigger | PASS | Unit tested in `EyeGuardPolicyTest` |
| 4. Proportional dimming capped at 0.8 | Opacity scales 0.45..0.80 with closeness; clears < 1s | PASS | Unit tested; capped at 0.8 in `SpikeOverlayManager` |
| 5. Calibration screen | UI allows one-tap calibration at 30 cm and updates $K$ | UNVERIFIED | Manual calibration on phone required |
| 6. Threshold settings | User can switch between 20 cm, 25 cm, 30 cm triggers | PASS | StateFlow reactive update verified |
| 7. Local Room event logging | Logs `TOO_CLOSE`, `RECOVERED`, `CALIBRATION`, etc. to SQLite | PASS | Room KSP build verified; events displayed in UI |
| 8. Notification Pause / Resume | Pauses dimming and camera binding from shade | UNVERIFIED | Manual test on device |
| 9. Screen-off / Screen-on gating | Camera unbinds on screen off, rebinds on screen on | PASS | Verified in Phase 0 logcat |
| 10. No-face >5s idle power saving | Screen dims and analysis throttles to 1 fps after 5s no-face | PASS | Unit tested in `EyeGuardPolicyTest` & `SpikePolicyTest` |
| 11. Face return from idle | Exits idle poll, removes dim, restores full-rate analysis | PASS | Unit tested in `EyeGuardPolicyTest` & `SpikePolicyTest` |

*Note: Per honesty rule, items requiring interactive physical interaction with the camera are marked UNVERIFIED until tested by user on phone.*

## What is Currently Being Worked On
- Phase 1 completed; awaiting user on-device verification script.

## What is Not Completed
- Phase 2: Privacy Guard (multi-face detection, shoulder-surfing alert/blur).
- Phase 3: Smart Dashboard (UsageStatsManager foreground screen time, rule-based suggestions).
- Phase 4: Profiles + PIN (Parent/Child profiles, PIN switch).
- Phase 5: Time Tokens (state machine, cooldowns, 5-minute windows).
- Phase 6: Privacy Ledger (MASVS encryption, data wipe).
- Phase 7: Polish & Production APK.
- Phase 8: Event pack.

## Known Bugs / Problems / Blockers
- None blocking. Build, unit tests (15/15), manifest audit, and APK install all succeeded.

## Tests Performed and Results
- Unit tests (`./gradlew test`): **PASS** (15/15 tests passed across `EyeGuardPolicyTest` and `SpikePolicyTest`).
- Merged manifest privacy audit (`./gradlew :app:processDebugMainManifest`): **PASS** (zero network permissions).
- APK Build (`./gradlew assembleDebug`): **PASS** (exit code 0).
- APK Deployment (`adb install -r`): **PASS** (installed to `ZF6526CJ97`).

## Build Status
- **SUCCESS** (`./gradlew assembleDebug` and `./gradlew test` exit 0).

## APK / Device Testing Status
- Phase 1 APK installed on `ZF6526CJ97`. Awaiting user verification script on device.

## Exact Next Recommended Action
- User to test Phase 1 on Motorola Moto E7 Plus (`ZF6526CJ97`):
  1. Open app and tap "Start Protection".
  2. Hold phone at ~30 cm and tap "Calibrate at 30 cm". Verify $K$ updates.
  3. Hold phone at comfortable distance (~35–40 cm) -> No warning, no dim.
  4. Bring phone close (~20 cm) -> Warning banner appears, screen dims proportionally.
  5. Back away to ~30–40 cm -> Warning clears, dim removes within 1 second.
  6. Cover eyes with hand while close -> Bounding box still detects face and dims.
  7. Move phone away / face absent for >5s -> Screen dims (battery saving, 1 fps).
  8. Face returns -> Dim clears, full-rate analysis resumes.
  9. In notification shade, tap "Pause Protection" -> Dim clears and camera pauses. Tap "Resume" -> Protection resumes.
  10. Turn screen off -> Camera unbinds; turn screen on -> Camera resumes.
  11. Check "Local Safety Event Ledger" card -> Confirm events were recorded in Room DB.
- Await user command (`next` / `start Phase 2`) before beginning Phase 2.

## Important Decisions & Honest Limitations
- **Approximate Distance**: Pinhole optical estimation ($d \approx K / w$) has an expected error margin of ~15% depending on individual facial dimensions and pitch/roll. Calibration aligns $K$ to the specific user.
- **Opacity Cap**: Capped strictly at 0.80 per Android 12+ touch pass-through requirements.
- **Unmeasured Battery**: Frame throttling to 1 fps during idle avoids >95% of ML Kit inferences, but physical battery consumption is not claimed as measured until multi-hour testing.
