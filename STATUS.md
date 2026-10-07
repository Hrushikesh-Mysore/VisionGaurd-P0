# VisionGuard Status

## Current Phase
**Phase 2: Privacy Guard (Completed - Verified in JVM Unit Tests & Deployed on Device)**

## What is Completed
- **Clean Multi-Face Observation Abstraction**:
  - Pure Kotlin `DetectedFace(widthFraction, yaw)` and `FaceObservation(widthFraction, yaw, count, timestampMs, secondaryFaces)` decoupled from raw frame bitmaps and landmarks.
  - Distinguishes owner face (largest face in view) from secondary faces.
- **Privacy Guard Policy Engine**:
  - Pure Kotlin `PrivacyGuardPolicy` taking time via `Clock` interface for 100% deterministic JVM testing with zero Android framework imports.
  - Identifies qualifying secondary viewers: `widthFraction >= 0.10` and `abs(yaw) < 35°` (facing screen).
  - $N = 3$ consecutive-frame confirmation eliminates spurious triggers from passing faces.
  - Automatic clearance: resets alert and overlay after 2 seconds ($2000\text{ ms}$) of secondary viewer absence.
  - Manual dismissal: allows immediate suppression of active alerts, holding suppression until the viewer leaves for $\ge 2$ seconds and later returns.
- **Guard Priority Architecture**:
  - Explicit priority engine: **Privacy Guard shield takes precedence over Eye Guard dimming**.
  - A shoulder-surfing intrusion represents an urgent confidentiality breach requiring immediate visual obscuring of sensitive screen content, which supersedes ergonomic viewing distance dimming.
- **Enhanced Overlay System (`SpikeOverlayManager`)**:
  - Dual overlay mode support: `EYE_GUARD_DIM` (proportional black dimming 0.45..0.80) and `PRIVACY_GUARD_SHIELD` (frosted dark navy slate at 0.80 opacity).
  - Hardware cross-window blur on API 31+ (`FLAG_BLUR_BEHIND` and `blurBehindRadius = 45`) when supported by the device window manager.
  - Click-through behavior preserved (`FLAG_NOT_TOUCHABLE`) strictly adhering to Android 12+ touch pass-through rule C3 ($\le 0.80$ opacity).
  - External dismissal controls: provided via notification shade action and in-app button (documented trade-off).
- **User Interface & Controls**:
  - Privacy Guard on/off toggle switch on Home screen.
  - Prominent Privacy Alert banner with one-tap "Dismiss Privacy Shield" control.
  - Live Multi-Face Detection Metrics HUD displaying total faces, secondary viewers, confirmation frames, privacy state, and active overlay mode.
  - In-app and notification dismissal actions (`ACTION_DISMISS_PRIVACY`).
- **Room Database Event Logging**:
  - Offline Room database logging for Privacy Guard events (`PRIVACY_ALERT`, `PRIVACY_RECOVERED`, `PRIVACY_DISMISSED`, `PRIVACY_TOGGLED`).
- **Testing & Deployment**:
  - JVM unit tests: **23/23 passed** (8 in `PrivacyGuardPolicyTest`, 7 in `EyeGuardPolicyTest`, 8 in `SpikePolicyTest`).
  - Merged manifest privacy audit: **PASS** (`INTERNET` and `ACCESS_NETWORK_STATE` strictly absent).
  - Debug APK built and installed on connected Motorola Moto E7 Plus (`ZF6526CJ97`, Android 10).

## Phase 2 Feature Verification Table

| Item | Target Behavior | Status | Verification Method |
|---|---|---|---|
| 1. Multi-face observation model | `DetectedFace` & `FaceObservation` mapping secondary viewers | PASS | Unit tested (23/23) & ML Kit pipeline |
| 2. Secondary viewer criteria | `widthFraction >= 0.10` and `abs(yaw) < 35°` | PASS | Unit tested in `PrivacyGuardPolicyTest` |
| 3. N=3 frame confirmation | Requires 3 consecutive qualifying frames to trigger | PASS | Unit tested in `PrivacyGuardPolicyTest` |
| 4. 2-second absence clearance | Clears trigger 2 seconds after second face leaves | PASS | Unit tested in `PrivacyGuardPolicyTest` |
| 5. Manual dismissal | User dismissal suppresses alert; re-arms on departure | PASS | Unit tested in `PrivacyGuardPolicyTest` |
| 6. Guard priority (Privacy wins) | Privacy shield supersedes Eye Guard dimming | PASS | Unit tested & verified in policy/overlay |
| 7. Frosted shield overlay | Frosted dark navy slate overlay capped at 0.80 opacity | PASS | Verified in `SpikeOverlayManager` |
| 8. API 31+ blur-behind fallback | Activates `FLAG_BLUR_BEHIND` on API 31+; graceful fallback | PASS | Verified in `SpikeOverlayManager` |
| 9. Privacy on/off toggle | Switch on Home enables/disables Privacy Guard | PASS | Compose UI & StateFlow verified |
| 10. External dismissal controls | Dismiss via notification shade action & in-app button | PASS | Verified in Service & Activity |
| 11. Room event logging | Logs `PRIVACY_ALERT`, `PRIVACY_RECOVERED`, etc. to SQLite | PASS | Room KSP build verified; events displayed in UI |
| 12. Physical multi-person test | Second person behind triggers alert; clears after 2s | UNVERIFIED | Manual physical test on phone required |

*Note: Per honesty rule, items requiring interactive physical interaction with multiple people in front of the camera are marked UNVERIFIED until tested by user on phone.*

## What is Currently Being Worked On
- Phase 2 completed; awaiting user on-device verification.

## What is Not Completed
- Phase 3: Smart Dashboard (UsageStatsManager foreground screen time, rule-based suggestions).
- Phase 4: Profiles + PIN (Parent/Child profiles, PIN switch).
- Phase 5: Time Tokens (state machine, cooldowns, 5-minute windows).
- Phase 6: Privacy Ledger (MASVS encryption, data wipe).
- Phase 7: Polish & Production APK.
- Phase 8: Event pack.

## Known Bugs / Problems / Blockers
- None blocking. Build, unit tests (23/23), manifest audit, and APK install all succeeded.

## Tests Performed and Results
- Unit tests (`./gradlew test`): **PASS** (23/23 tests passed across `PrivacyGuardPolicyTest`, `EyeGuardPolicyTest`, and `SpikePolicyTest`).
- Merged manifest privacy audit (`./gradlew :app:processDebugMainManifest`): **PASS** (zero network permissions).
- APK Build (`./gradlew assembleDebug`): **PASS** (exit code 0).
- APK Deployment (`adb install -r`): **PASS** (installed to `ZF6526CJ97`).

## Build Status
- **SUCCESS** (`./gradlew assembleDebug` and `./gradlew test` exit 0).

## APK / Device Testing Status
- Phase 2 APK installed on `ZF6526CJ97`. Awaiting user verification script on device.

## Exact Next Recommended Action
- User to test Phase 2 on Motorola Moto E7 Plus (`ZF6526CJ97`):
  1. Open app and verify "Privacy Guard (Shoulder Surfing)" switch is ON.
  2. Tap "Start Protection" (Eye Guard and Privacy Guard both active).
  3. Look at screen alone -> Normal operation, HUD shows 1 face, 0 secondary viewers.
  4. Have a second person look at the screen over your shoulder (or hold a photo/mirror/tablet with a face facing the camera) -> HUD shows Secondary Viewers count reach 3/3, Privacy Alert card appears, screen frosted with privacy shield, notification displays Privacy Alert.
  5. Tap "Dismiss Privacy Shield" in the app or notification shade -> Screen un-frosts; HUD notes dismissed state.
  6. Have the second person step away for > 2 seconds -> Alert completely clears; HUD resets to "MONITORING".
  7. When second person returns -> Alert triggers again after 3 frames.
  8. Test Guard Priority: Hold phone very close (< 20 cm) while second person is also looking -> Privacy frosted shield remains active (Privacy Guard wins). Have second person step away while still holding phone close -> Screen smoothly reverts to Eye Guard black dimming.
  9. Check "Local Safety Event Ledger" card -> Confirm `PRIVACY_ALERT`, `PRIVACY_RECOVERED`, and `PRIVACY_DISMISSED` events were recorded.
  10. Toggle Privacy Guard switch OFF -> Verify second viewer is ignored and screen does not frost.
- Await user command (`next` / `start Phase 3`) before beginning Phase 3.

## Important Decisions & Honest Limitations
- **Approximate Distance**: Pinhole optical estimation ($d \approx K / w$) has an expected error margin of ~15% depending on individual facial dimensions and pitch/roll. Calibration aligns $K$ to the specific user.
- **Overlay Opacity Cap**: Capped strictly at 0.80 per Android 12+ touch pass-through requirements.
- **Click-Through Trade-Off**: The privacy overlay is click-through (`FLAG_NOT_TOUCHABLE`) so user is not blocked from interacting with apps, requiring external dismissal controls via notification and in-app buttons.
- **Unmeasured Battery**: Frame throttling to 1 fps during idle avoids >95% of ML Kit inferences, but physical battery consumption is not claimed as measured until multi-hour testing.
