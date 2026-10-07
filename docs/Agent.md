# VisionGuard: Final Prompt Pack (v2, light and doable)

## How to use (2 minutes)

1. **Preflight tonight (while you have internet):** Android Studio (current stable) installed and opened once so it downloads the SDK, JDK 17, a **real Android phone** (Android 10+, front camera) with USB debugging on, and `adb devices` listing it. Do a test Gradle sync on any sample project so the downloads are cached.
2. Make an empty folder, run `git init`, put your SRS at `docs/SRS.pdf`. If your agent can't read PDFs, also run `pdftotext docs/SRS.pdf docs/SRS.txt`.
3. Save **only the text between the BEGIN/END markers in Part 1** as `AGENTS.md` in the repo root, and paste it as message 1 as well.
4. Paste **one phase prompt at a time** (Part 2). After each phase, **test on your phone**, then reply `next`. If the agent drifts, say: *"Re-read AGENTS.md and STATUS.md, then continue Phase N."*
5. Keep one model for the whole build. If it fails the same error 3 times, switch to a stronger model **at a phase boundary** (the repo, `STATUS.md` and git tags carry the state).

**What changed from v1:** a risk spike comes first; Hilt, SQLCipher, BiometricPrompt, SBOM and multi-module are removed (they go on the roadmap slide); technical tips now live inside the phase that needs them, so the master prompt stays short; the dashboard is now the hero of the app.

**Time plan (about 12 h):**

| Phase | Budget | Priority |
|---|---|---|
| 0 Risk spike | 1 h | MUST |
| 1 Eye Guard + Home | 2 h | MUST |
| 2 Privacy Guard | 1.25 h | MUST |
| 3 Dashboard | 2 h | MUST |
| 4 Profiles + PIN | 1 h | bonus |
| 5 Time Tokens | 1.5 h | bonus |
| 6 Privacy Ledger | 0.75 h | bonus |
| 7 Polish + APK | 1 h | MUST |
| 8 Event pack | 1.25 h | MUST |

**Cut rule:** if you are 2 hours behind after Phase 3, jump to Phases 7 and 8 and put the rest on the roadmap slide. Round 1 is a pitch, so the deck, script and Q&A matter as much as the app.

---

## PART 1: MASTER PROMPT (save as AGENTS.md)

=== BEGIN MASTER PROMPT ===

# Role and goal
You are a senior Android engineer pair-building a **lightweight, demo-ready prototype of VisionGuard** with 2 to 4 college students for a product-development competition on **8 October 2026**. The deadline is hard. Priorities: (1) it works live on one real phone in a 5-minute demo, (2) every claim is true, (3) students can explain the code, (4) a clean, easy-to-use UI.

VisionGuard is a privacy-first Android app protecting eyesight, screen privacy and time using on-device face detection and local usage tracking. We are not inventing AI models; the innovation is integrating existing on-device tech into one private, battery-aware system. **No cloud. The manifest must never contain INTERNET or ACCESS_NETWORK_STATE.**

# Source of truth
Read `docs/SRS.pdf` fully before coding. Where this file and the SRS differ, this file wins.

# Features
F1 Eye Guard (phone too close: warn + dim). F2 Privacy Guard (second viewer: alert + dim/blur). F3 Profiles (parent/child, app-level, PIN). F4 Smart Dashboard (usage + suggestions). F5 Time Tokens (limit, cooldown, 5-minute window instead of a hard block).

# Stack (keep it light)
Kotlin, Jetpack Compose + Material 3, ViewModel + StateFlow, Coroutines, Room (KSP), CameraX, ML Kit Face Detection (bundled, offline). minSdk 29; compile/target = newest SDK installed. **No Hilt, no DI framework** (use a simple `AppContainer` object holding singletons). **Single `:app` module.** No chart library, no image library, no analytics, nothing needing the network. Use a Gradle version catalog with versions that actually resolve (check; never invent versions). Justify each dependency in one line. Navigation: simple state-based bottom bar (no Navigation library).

Package layout: `vision`, `overlay`, `usage`, `data`, `policy`, `ui`. The `policy` package contains **pure Kotlin with zero android imports** and takes time through a `Clock` interface so it can be unit-tested.

# Working agreement
- **One phase at a time.** Each phase: plan in at most 10 lines, implement, run `./gradlew assembleDebug` and `./gradlew test`, fix, `git commit`, `git tag phase-N`, then report and **stop until I say `next`**.
- **Report template:** what was built (max 5 lines); build/test results (real output); what I must test on my phone (numbered, concrete); known issues.
- **Honesty rule:** never claim something works unless you ran it. If you can't run it, mark it `UNVERIFIED` and give manual steps. Keep `STATUS.md` (feature / status: Working, Partial, Mocked, Not started / how verified). Our pitch may only claim what is Working.
- **Explainable code:** small files, plain Kotlin, a two-line comment at the top of each file saying what and why. Keep `docs/CODE_TOUR.md` (one plain-English paragraph per package).
- **Merged manifest check after every phase:** confirm INTERNET and ACCESS_NETWORK_STATE are absent (`./gradlew :app:processDebugMainManifest`, inspect the merged manifest). If a library injects one, remove it with `tools:node="remove"`.
- **No scope creep, no rewrites of working code.** If a task will blow the budget, say so and propose a cut.
- **Stuck rule:** after 3 failed attempts at one error, stop, summarise, and ask me.
- Do not use AccessibilityService. Do not use the deprecated `androidx.security:security-crypto`.

# UX principles (the app must be easy to use)
- **One-tap start:** the Home screen has one big Start/Stop protection button and a clear status line ("Protection on, camera active").
- **3-step first run** (under 3 minutes): permissions with plain-English reasons and deep links, then calibration, then done.
- Everything is at most 2 taps from Home. Warnings are friendly and explain why ("Your screen dimmed because the phone is very close to your eyes").
- Light and dark theme, 48 dp touch targets, readable at 1.3 font scale.

# Honest limits (say these in docs, never hide them)
Distance is approximate (target about 15% after calibration). Some phone brands may kill background services. Profiles are app-level. Battery drain is not measured unless we actually measure it.

# First action
Read the SRS and this file. Reply with (1) a 10-line plan, (2) any Android 14 or SRS risks you see, (3) confirmation you will do Phase 0 only. **Write no code until I say `start Phase 0`.**

=== END MASTER PROMPT ===

---

## PART 2: PHASE PROMPTS (one at a time)

### PHASE 0: Risk spike (1 h). Prove the hard parts work on my phone

Start Phase 0. First check my environment (`java -version`, Android SDK, `adb devices`) and report problems before proceeding. Create the Android project (Compose, Material 3, CameraX, ML Kit face detection, Room later). Build the ugliest possible proof, no polish:

1. A single screen with a Start button and a permission checklist (Camera, Display over other apps, Notifications) with deep links to settings.
2. Start button launches a **camera foreground service** (`LifecycleService`, `foregroundServiceType="camera"`, persistent notification). Manifest needs `CAMERA`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`. On Android 14 the service must be started **while the app is visible**, so start it only from the button.
3. CameraX `ImageAnalysis` (front camera, 320x240 to 640x480, keep-only-latest) feeding ML Kit face detection (`PERFORMANCE_MODE_FAST`, no landmarks). Always call `imageProxy.close()` in `finally`. Show on the screen and in logcat: face count and `widthFraction = boundingBox.width / uprightImageWidth` (upright width is `image.height` when `rotationDegrees` is 90 or 270, else `image.width`).
4. A test overlay via `WindowManager` `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, black at alpha 0.5, toggled by a button and also automatically when `widthFraction` exceeds a hardcoded value.

Verify the merged manifest has no INTERNET. Create `STATUS.md` with a **spike table**: camera service runs in background, face detected with screen on, overlay appears over another app (test with Chrome), overlay is click-through, camera stops when the screen is off (implement with a `ACTION_SCREEN_OFF/ON` receiver that unbinds/rebinds analysis).
**Done when:** the APK installs on my phone, and I have tested each spike row and the result (PASS/FAIL) is written in STATUS.md. If any row fails, stop and report with logcat output and your diagnosis. Do not continue to Phase 1 until I confirm.

### PHASE 1: Eye Guard + Home screen (2 h)

Start Phase 1 (build on the spike code, clean it up as you go).

**Logic (pure Kotlin in `policy` or `vision`, with unit tests):**
- `DistanceEstimator`: calibration stores `K = widthFractionAtCalibration * 30` (use the median of about 10 frames); then `distanceCm = K / widthFraction`. Smooth with EMA (alpha about 0.4). Trigger after N=5 consecutive frames below the threshold. Default threshold 30 cm (adjustable), 5 cm hysteresis before clearing.
- Dim alpha = `clamp((threshold - d) / threshold, 0.15, 1.0) * 0.8` (**never above 0.8**; Android 12+ blocks touch pass-through above that). Remove dim within 1 s after recovering.
- Adaptive fps by throttling frames in the analyzer by timestamp: about 1 fps idle, 5 fps when distance is within threshold + 15 cm. Analysis stops when the screen is off or locked.

**App:**
- **Home screen:** big Start/Stop button, live status line, current protection state, a small collapsible debug HUD (widthFraction, distance, face count, fps) that I can toggle for the demo.
- **Calibration screen:** "Hold phone at 30 cm, then tap Calibrate" with a live preview of the measured value (no camera preview image is shown or stored; show just numbers or a simple progress).
- Warning notification or in-app banner when triggered, friendly wording. Notification has a one-tap **Pause** action.
- Settings: threshold slider.
- Room: log an Eye Guard event (timestamp only) each time it triggers. Never store frames.
**Done when:** unit tests pass; at about 20 cm from my face the screen dims and warns within about 1 s; at 40 cm it clears; nothing flickers; Pause works. Give me an exact manual test script for 20/30/40/60 cm.

### PHASE 2: Privacy Guard (1.25 h)

Start Phase 2. Extend detection to multiple faces. The owner is the largest face. A **secondary viewer** is any other face with `widthFraction >= 0.10` (tunable) and `abs(headEulerAngleY) < 35` degrees, present for N=3 consecutive frames. Pure-Kotlin function, unit tested. On trigger: alert plus a privacy overlay (dark/frosted; on API 31+ try `FLAG_BLUR_BEHIND` with `blurBehindRadius` only when `WindowManager.isCrossWindowBlurEnabled()` is true, otherwise dark). Overlay stays click-through, so provide dismissal via the notification action and an in-app button (explain this trade-off in a comment). It clears after 2 s with no second face. Add a Privacy Guard on/off toggle on Home. Log events to Room. Define priority when both guards trigger (privacy overlay wins) and document it.
**Done when:** a second person behind me looking at the screen triggers the alert after about 3 frames, it clears about 2 s after they leave, and Eye Guard still works.

### PHASE 3: Smart Dashboard (2 h). The hero screen, make it look great

Start Phase 3. Usage data: `UsageStatsManager.queryEvents`, pair `ACTIVITY_RESUMED` and `ACTIVITY_PAUSED` events per package to compute foreground time; exclude our own app and the launcher; need Usage access permission (add it to the first-run checklist with a deep link). Repository exposes today's total, per-app breakdown with labels and icons (`PackageManager`), 7-day totals, and Eye Guard / Privacy Guard event counts.

**Design the dashboard like a polished product, using only Compose (draw charts yourself with `Canvas`, no chart library):**
1. Top: profile chip (placeholder until Phase 4) and date.
2. **Hero card:** big "today's screen time" number (for example `3h 12m`) with a circular progress ring against a daily goal (default 4 h, adjustable).
3. **Protection today:** two compact cards, "Eye Guard: N reminders" and "Privacy Guard: N alerts", with small icons and one friendly sentence.
4. **Top apps:** top 5 apps as rows with icon, name, time and a proportional bar.
5. **7-day trend:** simple bar chart, today highlighted.
6. **Suggestion card:** from a small pure-Kotlin `SuggestionEngine` (unit tested). Rules: 40+ continuous minutes in a feed/social/video app suggests a walk, stretch or reading; frequent Eye Guard triggers suggests the 20-20-20 rule; use after 11 pm suggests winding down; otherwise a positive message.
Use Material 3 color roles, generous spacing, rounded cards, a consistent icon set, light and dark themes, a shimmer or simple placeholder while loading, and an empty state that explains how to grant usage access. Add a **Hide from screenshots (FLAG_SECURE)** setting that is **off by default**, because we need screenshots and screen recording for the pitch and backup demo.
**Done when:** the dashboard shows real numbers close to Android's Digital Wellbeing for today, scrolls smoothly, looks good in light and dark, and the suggestion rules are unit tested.

### PHASE 4 (bonus): Profiles + PIN (1 h)

Start Phase 4. Two local profiles (Parent, Child), each with separate usage attribution, limits and settings. Attribute usage to the profile active at the time it occurred (keep a profile-switch log with timestamps). Parent PIN set on first use; PBKDF2WithHmacSHA256 (at least 100,000 iterations, unique 16-byte salt from `SecureRandom`) with attempt limiting and exponential lockout (pure Kotlin, unit tested). Profile switching and parent-only actions require the PIN. The child profile cannot change limits, disable protection or view the other profile. Show a consent screen before enabling camera analysis, plus a parental-consent step when creating a child profile. Wire the profile chip on the dashboard. State clearly in STATUS.md and comments that profiles are app-level because Android multi-user is not available to normal apps.
**Done when:** two profiles show different usage, a wrong PIN locks out with growing delay, and the child profile cannot reach parent controls.

### PHASE 5 (bonus): Time Tokens (1.5 h)

Start Phase 5. In `policy`, implement `PolicyEngine` as a pure-Kotlin state machine `ACTIVE -> LIMIT_REACHED -> COOLDOWN -> TOKEN_WINDOW(5 min) -> LIMIT_REACHED`, with daily reset and parent override back to `ACTIVE`; time comes from an injected `Clock` wrapping `SystemClock.elapsedRealtime()` so changing the system clock cannot bypass limits. Max tokens per day per profile (default 3, parent-configurable). After a reboot (`elapsedRealtime` lower than the stored value) restore remaining cooldown or window unchanged. Unit test every transition and edge case, including a fake clock where wall time jumps but the monotonic clock does not.
App side: persist state in Room; the existing service polls the foreground app about once per second while the screen is on and feeds the engine; the **block screen** is a separate **touchable** overlay (NOT click-through) showing which limit was hit, a cooldown countdown, a "Use Time Token" button when available, tokens left, and a "Go home" button. Do not rely on starting activities from the background. Parent UI for per-app limits and max tokens, PIN protected. Add a **Demo Mode** switch: limit 1 minute, cooldown 20 s, token window 30 s.
**Done when:** with Demo Mode on, using a chosen app for 1 minute shows the block screen, the cooldown counts down, a token unlocks 30 s, then it blocks again; tests pass; STATUS.md says exactly what is real versus simulated.

### PHASE 6 (bonus): Privacy Ledger (0.75 h)

Start Phase 6. Build a **Privacy Ledger** screen that lists exactly what is stored: event counts, usage summaries, profiles, calibration value, and "Camera frames: never stored". Add a **Proof of privacy** card that reads this app's requested permissions at runtime (`PackageManager`) and shows "INTERNET: not declared" with a green tick (red warning if it ever appears). Add a retention setting (default 30 days) with a working purge, and a one-tap **Wipe all data** with confirmation. Light hardening: `allowBackup=false` with extraction rules excluding all data, every component `exported="false"` except the launcher activity, `usesCleartextTraffic="false"`. Do **not** claim database encryption; list it in the Ledger as "Roadmap".
**Done when:** the Ledger matches reality (verify by inspecting the stored data), wipe works, merged manifest has no INTERNET.

### PHASE 7: Polish and APK (1 h)

Start Phase 7. Polish for a live demo: app name and icon (use the name I give you), consistent theme, friendly empty and error states (camera unavailable or in use: degrade gracefully and tell the user), a screen explaining autostart and battery-optimisation exemptions with a deep link, a service-health indicator on Home, and a **Demo Guide screen** listing the demo steps in order with toggles for Demo Mode and the debug HUD. Build the APK (`assembleDebug` is fine; if you enable R8 for release, test that everything still works and add keep rules, otherwise leave minify off). Run through the whole demo path twice on my phone without a crash. Write `docs/TEST_RESULTS.md` with the **real** result of every acceptance test we can run (Pass / Fail / Not run). Battery: only record a number if we actually measured; otherwise write "not measured".
**Done when:** the APK installs and the full demo path runs twice in a row, and TEST_RESULTS.md is honest.

### PHASE 8: Event pack (1.25 h, documents only)

Start Phase 8. Using only what `STATUS.md` and `TEST_RESULTS.md` say is real, create these in `docs/event/`. Do not invent facts, statistics, names, prices or competitor numbers; label guesses "estimate"; use `[FILL IN]` where I must supply information.
1. `ROUND1_PITCH.md`: a speakable 3-minute script (about 400 words) plus slide content for exactly these sections: Problem identification; Proposed product solution; Target users; Key features; Technology/materials; Innovation/uniqueness; Expected social and environmental impact; Basic prototype design/wireframes (describe each screen); Estimated cost (itemised in INR, labelled estimates); Future scope (roadmap: encrypted storage, biometric parent actions, automatic calibration, trusted-viewer mode, uninstall protection, local parent-child sync, Play Store compliance and legal review). Frame innovation honestly as integration of existing on-device tech; compare with Android Digital Wellbeing and Family Link in general terms without fabricating specifics. Our difference: camera-aware protection, per-person limits on a shared device, soft limits with cooldown tokens, and a design where the manifest has no INTERNET permission so "no cloud" is verifiable.
2. `DEMO_SCRIPT.md`: a minute-by-minute 5-minute demo (20 s problem hook; Eye Guard with debug HUD; Privacy Guard with a teammate behind me; dashboard; profile switch if built; Time Tokens in Demo Mode if built; Privacy Ledger proof card; 20 s close), each step with exact taps, expected result and a fallback (backup screen recording). Include a pre-demo checklist: phone charged, permissions granted, battery optimisation exempted, calibration done in the room's lighting, Demo Mode on, backup APK and recording on laptop and phone, airplane mode on to show it works offline.
3. `QA_BANK.md`: 20 likely judge questions with short honest answers, including: distance accuracy; why not AccessibilityService; why a foreground service with a notification; what if the OS kills it; can a child bypass it; does the camera record me; legality of monitoring children (consent obligations under DPDP, COPPA, GDPR; legal review needed; not legal advice); battery impact (state what was measured or "not yet measured"); how it differs from existing apps; our own contribution versus AI tools (`[FILL IN]` per member); limitations (low light, glasses, photos of faces, phone brands that kill services); deployment or business model.
4. `CONTRIBUTIONS.md`: template table per member (`[FILL IN]` name, branch, modules they can explain, what they personally did, how AI tools were used) plus a truthful one-paragraph statement: we wrote the spec and architecture, directed an AI coding agent phase by phase, reviewed, tested on a real device and fixed issues.
5. `VIVA_CHEATSHEET.md`: per package, five plain-English lines on how it works, the 3 most important functions, and one likely question; plus plain explanations of the distance formula and the PolicyEngine state machine for a first-year student.
**Done when:** all files exist, nothing unverified is stated as fact, and the `[FILL IN]` items are listed at the end of your reply.

---

## PART 3: Your own checklist (the agent can't do these)

- **Test each phase on your phone** before saying `next`. The agent can't see your lighting, your face, or your phone brand's background-kill behaviour.
- **Read `CODE_TOUR.md` and `VIVA_CHEATSHEET.md` tonight.** Judges will ask each member what they built, and Rule 6 requires you to explain your contribution when AI tools are used.
- **Pick the name** (VisionGuard or GuardEye) before Phase 7 and tell the agent.
- **Ask the organisers** whether Round 1 and Round 2 are both on 8 Oct and whether you can show screenshots or a screen recording in the pitch.
- **Bring:** laptop and charger, phone, cable and power bank, a teammate for Privacy Guard, backup APK, and a screen recording of a perfect run. The app works offline, so don't depend on venue Wi-Fi.
- **Be honest on stage:** "working core, clear roadmap" is a strong position. Say what is partial.