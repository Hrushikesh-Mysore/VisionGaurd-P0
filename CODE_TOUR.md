# VisionGuard Code Tour

## Architecture Overview
VisionGuard is implemented as a single `:app` module Android application built with Jetpack Compose, Material 3, Coroutines/StateFlow, Room, CameraX, and bundled ML Kit Face Detection. The application is strictly offline and privacy-first (no network permissions declared). Dependency injection is handled cleanly via a lightweight `AppContainer` singleton rather than third-party DI frameworks.

## Planned Package Structure
- `vision`: CameraX `ImageAnalysis` pipeline, lifecycle-aware camera management, and ML Kit Face Detection wrapper. Runs only when screen is on and unlocked. Frames are processed strictly in-memory and immediately discarded.
- `overlay`: System overlay management using `WindowManager` (`TYPE_APPLICATION_OVERLAY`). Handles click-through dimming overlays for Eye Guard and Privacy Guard alerts, respecting Android 12+ touch pass-through limits ($\le 0.8$ opacity).
- `usage`: Local screen time and application usage aggregation using Android's `UsageStatsManager` events (`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`).
- `policy`: Pure Kotlin domain logic with zero Android framework imports. Houses `DistanceEstimator`, `SuggestionEngine`, and `PolicyEngine` state machines. Operates against an injectable `Clock` abstraction for testability.
- `data`: Local Room database (KSP) and repository layers for storing derived events, user calibration constants, and profiles. No raw frames or facial embeddings are stored.
- `ui`: Jetpack Compose user interface, including Home screen, Smart Dashboard, Calibration screen, Privacy Ledger, and profile controls. Navigation uses simple state-based bottom bar navigation.

## Current State
Repository initialized. Android project scaffolding will be created upon environment readiness for Phase 0.
