# Changelog

All notable changes to the VisionGuard project will be documented in this file.

## [Phase 2 - Privacy Guard] - 2026-10-07
### Added
- Multi-face observation model in `FaceObservation` with `DetectedFace` (width fraction, yaw angle) distinguishing owner from secondary viewers.
- Pure Kotlin `PrivacyGuardPolicy` with zero Android imports and `Clock` abstraction:
  - Detects secondary viewers facing the screen (`widthFraction >= 0.10`, `abs(yaw) < 35°`).
  - Confirms condition for $N = 3$ consecutive frames before triggering alert.
  - Automatically clears alert and overlay after 2 seconds of secondary absence.
  - Supports manual dismissal suppressing re-triggering until the viewer leaves and returns.
- Guard Priority Engine: Privacy Guard frosted shield takes precedence over Eye Guard dimming.
- Enhanced `SpikeOverlayManager` with `OverlayMode` support:
  - Frosted dark navy slate privacy shield (`PRIVACY_GUARD_SHIELD`).
  - Hardware cross-window blur on API 31+ (`FLAG_BLUR_BEHIND` and `blurBehindRadius`) when supported.
  - Click-through behavior preserved (`FLAG_NOT_TOUCHABLE`) with opacity strictly capped at 0.80 for Android 12+ touch pass-through rule C3.
  - Explanatory comment documenting trade-off for external dismiss controls.
- Interactive Dismissal Controls:
  - One-tap "Dismiss Shield" action in persistent notification shade.
  - In-app "Dismiss Privacy Shield" button on home screen.
- Privacy Guard on/off toggle switch on Home screen.
- Room database logging for Privacy Guard events (`PRIVACY_ALERT`, `PRIVACY_RECOVERED`, `PRIVACY_DISMISSED`, `PRIVACY_TOGGLED`).
- Extended Live Detection Metrics HUD displaying total faces, secondary viewers, confirmation frames, privacy guard state, and active overlay mode.
- Comprehensive JVM unit test suite (`PrivacyGuardPolicyTest`) with 8 tests covering single face, small/turned faces, 3-frame confirmation, 2s absence clearance, manual dismissal, and guard priority (23/23 unit tests passing across all suites).
- Merged manifest privacy audit confirmed: zero network permissions (`INTERNET`, `ACCESS_NETWORK_STATE` strictly absent).
- Debug APK deployed and verified on connected test phone (`ZF6526CJ97`, Motorola Moto E7 Plus, Android 10).

## [Phase 1 - Eye Guard] - 2026-10-07
### Added
- Pure Kotlin `Clock` abstraction and `FaceObservation` domain model in `com.visionguard.policy`.
- `EyeGuardPolicy` supporting pinhole distance estimation, calibration ($K = d_{\text{cal}} \times w_{\text{cal}}$), Exponential Moving Average (EMA) smoothing ($\alpha = 0.35$), $N = 5$ consecutive-frame confirmation, hysteresis, and closeness-proportional dimming opacity capped at 0.80.
- Local offline Room database infrastructure (`AppDatabase`, `EyeGuardEventEntity`, `EyeGuardEventDao`) in `com.visionguard.data` configured with KSP (`2.0.20-1.0.25`).
- Proportional overlay dimming in `SpikeOverlayManager` scaling opacity from 0.45 to 0.80 while preserving touch pass-through.
- Calibration UI allowing users to calibrate viewing distance at ~30 cm reading distance.
- Adjustable Eye Guard threshold setting chips (20 cm default, 25 cm, 30 cm).
- Prominent user-facing warning banner and rate-limited notification alerts when phone is held too close.
- Local Safety Event Ledger displaying recent safety events from the Room database.
- Comprehensive JVM unit test suite (`EyeGuardPolicyTest`) with 7 tests verifying calibration, EMA, N-frame confirmation, hysteresis, and recovery timing (total 15 unit tests passing).

## [Phase 0 Final Fixes] - 2026-10-07
### Changed
- Centralized policy engine into `ProtectionPolicy` with explicit state machine (`NORMAL_DISTANCE`, `TOO_CLOSE`, `NO_FACE_GRACE_PERIOD`, `NO_FACE_DIMMED`).
- Proximity estimation tuned to ~20 cm trigger (`widthFraction >= 0.60`) and ~30 cm recovery (`widthFraction <= 0.45`) with hysteresis.
- Proximity strictly uses face bounding box geometry and does not require visible eyes or facial landmarks.
- No-face power-saving behavior: continuous no-face for $> 5$ seconds transitions to `NO_FACE_DIMMED`, enabling the screen dim overlay and throttling camera analysis to 1 fps idle polling to save battery.
- Seamless face return: detecting a face immediately exits `NO_FACE_DIMMED`, restores full-rate analysis, and maintains dim only if the returning face is within the ~20 cm proximity threshold.
- Interactive persistent notification controls ("Pause Protection" / "Resume Protection") that unbind camera hardware and suspend dimming without destroying the service.
- Maintained screen-off/screen-on lifecycle gating.
- Expanded JVM unit test suite to 8 passing tests in `SpikePolicyTest`.
- Confirmed zero-network permissions in merged Android manifest and installed debug APK on Motorola Moto E7 Plus (`ZF6526CJ97`).

## [Phase 0] - 2026-10-07
### Added
- Complete Android project scaffolding (Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.20, Compose BOM).
- Gradle version catalog (`gradle/libs.versions.toml`) with one-line dependency justifications.
- Pure-Kotlin `SpikePolicy` with JVM unit tests (`SpikePolicyTest`).
- `SpikeOverlayManager` for non-focusable, non-touchable system overlays (`TYPE_APPLICATION_OVERLAY` at 0.5 alpha).
- `CameraForegroundService` implementing `LifecycleService`, `foregroundServiceType="camera"`, persistent notification, dynamic `ACTION_SCREEN_OFF/ON` power gating, and bundled offline ML Kit face detection (`PERFORMANCE_MODE_FAST`).
- Compose UI (`MainActivity`) with Start/Stop button, permission checklist with settings deep links, live metrics HUD, and manual overlay toggle.
- Lightweight `AppContainer` singleton architecture without external DI frameworks.
- Clean manifest enforcement removing `INTERNET` and `ACCESS_NETWORK_STATE`.
- Successful APK build and installation on connected test device `ZF6526CJ97` (Motorola Moto E7 Plus, Android 10).

## [Pre-Phase 0] - 2026-10-07
### Added
- Extracted and verified SRS text from `docs/SRS.pdf` into `docs/SRS.txt`.
- Created `AGENTS.md` in repository root from Master Prompt instructions in `docs/Agent.md`.
- Initialized git repository on branch `main`.
- Created foundational project tracking documentation: `STATUS.md`, `CHANGELOG.md`, `CODE_TOUR.md`, and `HANDOFF.md`.
- Completed initial environment audit.
