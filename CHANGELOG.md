# Changelog

All notable changes to the VisionGuard project will be documented in this file.

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
