# VisionGuard Code Tour

## Architecture Overview
VisionGuard is implemented as a single `:app` module Android application built with Jetpack Compose, Material 3, Coroutines/StateFlow, CameraX, and bundled offline ML Kit Face Detection. The application is strictly offline and privacy-first (no network permissions declared). Dependency injection is handled cleanly via a lightweight `AppContainer` singleton rather than third-party DI frameworks.

## Package Tour

### `com.visionguard`
The root package contains `VisionGuardApp` (the custom `Application` class) and `AppContainer` (the centralized dependency container). `AppContainer` instantiates and holds singletons for overlay management and maintains the reactive `SpikeMetrics` `StateFlow` consumed by both the UI and background components without any reflection or runtime dependency injection overhead.

### `com.visionguard.policy`
This package contains pure Kotlin domain logic with zero Android framework imports. In Phase 0, it houses `SpikePolicy`, which calculates face width fractions relative to the upright image frame and evaluates proximity against calibrated thresholds. Because it contains no Android SDK classes, its entire behavior is verified by fast JVM unit tests (`SpikePolicyTest`).

### `com.visionguard.overlay`
This package manages system-level alert and dimming overlays using Android's `WindowManager`. In Phase 0, `SpikeOverlayManager` inflates a semi-transparent black view using `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`. It maintains an opacity of 0.5 (strictly $\le 0.8$) to guarantee touch pass-through to underlying applications in compliance with Android 12+ security rules.

### `com.visionguard.vision`
This package manages camera capture and machine vision processing. `CameraForegroundService` extends `LifecycleService` with `foregroundServiceType="camera"` and displays a persistent foreground notification. It configures CameraX with a front-camera `ImageAnalysis` use case streaming to ML Kit's offline face detector (`PERFORMANCE_MODE_FAST`). It registers dynamic broadcast receivers for `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON` to unbind and rebind camera analysis, conserving battery when the display is off.

### `com.visionguard.ui`
This package contains the Jetpack Compose user interface. In Phase 0, `MainActivity` renders `SpikeHomeScreen`, providing a one-tap Start/Stop protection toggle, an interactive checklist for Camera, Overlay, and Notification permissions with deep links to system settings, a live debug HUD displaying detected face counts and frame width fractions, and a manual overlay toggle.

### `com.visionguard.usage`
*(Planned for Phase 3)* Will house local foreground screen time aggregation using `UsageStatsManager` events (`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`).

### `com.visionguard.data`
*(Planned for Phase 1/3)* Will house local Room database schemas, DAOs, and repository layers for persisting derived events, user calibration constants, and profiles.
