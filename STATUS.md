# VisionGuard Status

## Current Phase
**Phase 3: Smart Dashboard (Completed - Verified in JVM Unit Tests & Deployed on Device)**

## What is Completed
- **Phase 0 & 1: Eye Guard (Verified)**:
  - Pinhole distance estimation, EMA smoothing ($\alpha = 0.35$), $N = 5$ consecutive-frame confirmation, hysteresis (~20 cm trigger, ~30 cm recovery), proportional dimming (0.45..0.80), calibration card, and 5-second no-face idle power saving.
- **Phase 2: Privacy Guard (Implementation Complete - Multi-Person Retest Pending)**:
  - Pure Kotlin `PrivacyGuardPolicy` with $N = 3$ frame confirmation, 2-second absence clearance, manual dismissal, and guard priority (privacy shield supersedes Eye Guard dimming).
  - Enhanced `SpikeOverlayManager` with frosted dark navy slate overlay (`PRIVACY_GUARD_SHIELD` at 0.80 alpha) and API 31+ hardware cross-window blur (`FLAG_BLUR_BEHIND` and `blurBehindRadius = 45`).
  - *Note: Multi-person physical verification is currently recorded as UNVERIFIED pending user physical test tomorrow.*
- **Phase 3: Smart Dashboard (Newly Completed)**:
  - **Foreground App Usage Tracking (`UsageRepository`)**:
    - Queries Android's `UsageStatsManager.queryEvents` locally on-device.
    - Accurately pairs `ACTIVITY_RESUMED` and `ACTIVITY_PAUSED` transitions per package to compute foreground time.
    - Excludes our own app, the default system launcher, and internal system processes.
    - Computes today's total foreground screen time, per-app breakdown with application labels and icons (`PackageManager`), percentage bars, and continuous feed session durations.
    - Aggregates 7-day usage history for trend charting.
  - **Pure Kotlin Rule-Based Suggestion Engine (`SuggestionEngine`)**:
    - Zero Android framework imports, tested with `Clock` abstraction.
    - Rule 1: $\ge 40$ continuous minutes in a feed/social/video app suggests a walk, stretch, or physical book.
    - Rule 2: Frequent Eye Guard triggers ($\ge 5$ times) suggests the 20-20-20 rule.
    - Rule 3: Late-night usage after 11 PM or before 5 AM suggests winding down for sleep.
    - Rule 4: Balanced screen habits return positive digital wellbeing encouragement.
    - 7/7 JVM unit tests passing in `SuggestionEngineTest`.
  - **Room Database Event Aggregation**:
    - Expanded `EyeGuardEventDao` with `getCountSince` queries for today's Eye Guard reminders (`TOO_CLOSE`) and Privacy Guard alerts (`PRIVACY_ALERT`).
  - **Hero Smart Dashboard UI (`SmartDashboardScreen`)**:
    - Date and profile chip header (placeholder for Phase 4 profiles).
    - Hero Card displaying today's total screen time and custom Compose `Canvas` circular progress ring against daily goal.
    - Daily goal selector chips (2h, 3h, 4h default, 6h).
    - Protection today summary cards displaying real counts for Eye Guard reminders and Privacy Guard alerts.
    - Dynamic rule-based suggestion card powered by `SuggestionEngine`.
    - 7-day trend bar chart drawn natively in Compose `Canvas` with today's bar highlighted in primary color.
    - Top 5 apps list with app icons, labels, formatted durations, and proportional progress indicators.
    - Screenshot shield switch (`FLAG_SECURE`, off by default for demo recording).
    - Empty state with direct deep link to system usage access settings.
  - **Navigation & Permissions Integration**:
    - Simple state-based bottom navigation bar in `MainActivity` with "Protection" (Eye & Privacy HUD) and "Dashboard" tabs without external navigation libraries.
    - Added `PACKAGE_USAGE_STATS` declaration to manifest and usage access deep link to the first-run permissions checklist.
- **Testing & Deployment**:
  - JVM unit tests: **30/30 passed** (7 in `SuggestionEngineTest`, 8 in `PrivacyGuardPolicyTest`, 7 in `EyeGuardPolicyTest`, 8 in `SpikePolicyTest`).
  - Merged manifest privacy audit: **PASS** (`INTERNET` and `ACCESS_NETWORK_STATE` strictly absent).
  - Debug APK built and installed on connected Motorola Moto E7 Plus (`ZF6526CJ97`, Android 10).

## Feature Verification Table

| Phase | Item | Target Behavior | Status | Verification Method |
|---|---|---|---|---|
| **Phase 1** | Distance estimation & calibration | $d \approx K / w$, one-tap calibration at ~30 cm | PASS | Unit tested (15/15) & physically tested by user on phone |
| **Phase 1** | Proportional dimming (0.45..0.80) | Scales opacity with proximity, clears < 1s | PASS | Unit tested & physically tested by user on phone |
| **Phase 1** | Screen-off / screen-on gating | Camera unbinds on screen off, rebinds on screen on | PASS | Logcat & physically tested by user on phone |
| **Phase 1** | No-face >5s idle power saving | Screen dims and analysis throttles to 1 fps | PASS | Unit tested & physically tested by user on phone |
| **Phase 2** | Secondary viewer detection | Second face with `widthFraction >= 0.10`, `abs(yaw) < 35°` | PASS | Automated unit tested in `PrivacyGuardPolicyTest` |
| **Phase 2** | N=3 confirmation & 2s clearance | Triggers after 3 frames, clears 2s after departure | PASS | Automated unit tested in `PrivacyGuardPolicyTest` |
| **Phase 2** | Guard priority (Privacy wins) | Privacy shield supersedes Eye Guard dimming | PASS | Automated unit tested in `PrivacyGuardPolicyTest` |
| **Phase 2** | Frosted shield overlay | Dark navy slate overlay (0.80 alpha, API 31+ blur) | PASS | Verified in `SpikeOverlayManager` on device |
| **Phase 2** | Physical multi-person verification | Second person behind triggers alert and frosted shield | **UNVERIFIED** | User had no second person available; will test tomorrow |
| **Phase 3** | Foreground time calculation | Pairs `ACTIVITY_RESUMED`/`PAUSED` via `UsageStatsManager` | PASS | Automated query verified on device with test appops |
| **Phase 3** | Top apps breakdown with labels/icons | Lists top 5 apps with icons, names, durations, bars | PASS | Automated query verified on device |
| **Phase 3** | 7-day trend history | Computes daily usage totals for past 7 days | PASS | Automated query verified on device |
| **Phase 3** | Pure Kotlin `SuggestionEngine` | Rule-based recommendations (feed, 20-20-20, night, balance) | PASS | Automated 7/7 unit tests passing in `SuggestionEngineTest` |
| **Phase 3** | Compose `Canvas` circular ring | Circular progress ring drawn via Canvas against goal | PASS | Verified in UI render on device |
| **Phase 3** | Compose `Canvas` 7-day chart | 7-day trend bar chart drawn via Canvas with today highlighted | PASS | Verified in UI render on device |
| **Phase 3** | State-based bottom navigation | Bottom bar switches between Protection and Dashboard | PASS | Verified in UI render on device |
| **Phase 3** | Screenshot shield (`FLAG_SECURE`) | Switch toggles FLAG_SECURE on window (off by default) | PASS | Verified in Compose UI on device |
| **Phase 3** | Physical Digital Wellbeing alignment | Real usage numbers match Android Digital Wellbeing | **UNVERIFIED** | Manual cross-check by user required |

*Note: Per honesty rule, items requiring interactive physical interaction with multiple people or manual cross-check with system settings are marked UNVERIFIED until tested by user on phone.*

## What is Currently Being Worked On
- Phase 3 completed; awaiting user on-device verification.

## What is Not Completed
- Phase 4: Profiles + PIN (Parent/Child profiles, PIN switch).
- Phase 5: Time Tokens (state machine, cooldowns, 5-minute windows).
- Phase 6: Privacy Ledger (MASVS encryption, data wipe).
- Phase 7: Polish & Production APK.
- Phase 8: Event pack.

## Known Bugs / Problems / Blockers
- None blocking. Build, unit tests (30/30), manifest audit, and APK install all succeeded.

## Tests Performed and Results
- Unit tests (`./gradlew test`): **PASS** (30/30 tests passed: 7 in `SuggestionEngineTest`, 8 in `PrivacyGuardPolicyTest`, 7 in `EyeGuardPolicyTest`, 8 in `SpikePolicyTest`).
- Merged manifest privacy audit (`./gradlew :app:processDebugMainManifest`): **PASS** (zero network permissions: `INTERNET` and `ACCESS_NETWORK_STATE` strictly absent).
- APK Build (`./gradlew assembleDebug`): **PASS** (exit code 0).
- APK Deployment (`adb install -r`): **PASS** (installed to `ZF6526CJ97`).
- Device Launch: Activity starts with zero runtime crashes or ANRs.

## Build Status
- **SUCCESS** (`./gradlew assembleDebug` and `./gradlew test` exit 0).

## APK / Device Testing Status
- Phase 3 APK installed on `ZF6526CJ97`. Awaiting user verification script on device.

## Exact Next Recommended Action
- User to test Phase 3 on Motorola Moto E7 Plus (`ZF6526CJ97`):
  1. Open app and observe bottom navigation bar with "Protection" and "Dashboard" tabs.
  2. Tap "Dashboard" tab:
     - If Usage Access is not granted, observe empty state card with "Grant Usage Access" button. Tap button, grant permission in Android settings, and return.
     - If Usage Access is granted (pre-granted via adb during install), observe Hero Card displaying real today's screen time (e.g. `Xh Ym`) and the circular progress ring.
  3. Verify Top Apps list:
     - Confirm up to 5 real installed apps are listed with labels, icons, formatted durations, and proportional progress bars (VisionGuard itself and launcher are excluded).
  4. Verify Protection Today cards:
     - Observe Eye Guard reminders count and Privacy Guard alerts count matching today's Room database records.
  5. Verify Suggestion Card:
     - Check recommended suggestion (e.g., Late Night wind-down if past 11 PM, or 20-20-20 rule if Eye Guard was triggered frequently, or Healthy Balance).
  6. Verify 7-Day Trend Bar Chart:
     - Check 7 vertical bars (past 6 days + today highlighted in primary color).
  7. Test Daily Goal Chips:
     - Tap 2h, 3h, 4h, 6h chips -> Observe circular progress ring percentage dynamically recalculates.
  8. Test Screenshot Shield (`FLAG_SECURE`):
     - Toggle ON -> Attempt screenshot or open app switcher (screen blocked/black in previews).
     - Toggle OFF -> Screenshot permitted (demo mode).
  9. Switch back to "Protection" tab -> Confirm Phase 0, 1, 2 HUD, camera monitor, and calibration cards continue functioning seamlessly.
- Await user command (`next` / `start Phase 4`) before beginning Phase 4.

## Important Decisions & Honest Limitations
- **Approximate Distance**: Pinhole optical estimation ($d \approx K / w$) has an expected error margin of ~15% depending on individual facial dimensions and pitch/roll.
- **Overlay Opacity Cap**: Capped strictly at 0.80 per Android 12+ touch pass-through requirements.
- **FLAG_SECURE Off by Default**: Screenshot protection is opt-in via a switch on the dashboard to allow demo recording and pitch slide captures.
- **Usage Access Local Only**: Usage statistics are aggregated strictly on-device using local `UsageStatsManager` events with zero network transmission.
- **Unmeasured Battery**: Frame throttling to 1 fps during idle avoids >95% of ML Kit inferences, but physical battery consumption is not claimed as measured until multi-hour testing.
