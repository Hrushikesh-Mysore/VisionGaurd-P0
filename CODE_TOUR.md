# VisionGuard Code Tour

## Architecture Overview
VisionGuard is implemented as a single `:app` module Android application built with Jetpack Compose, Material 3, Coroutines/StateFlow, Room with KSP, CameraX, and bundled offline ML Kit Face Detection. The application is strictly offline and privacy-first (no network permissions declared). Dependency injection is handled cleanly via a lightweight `AppContainer` singleton rather than third-party DI frameworks.

## Package Tour

### `com.visionguard`
The root package contains `VisionGuardApp` (the custom `Application` class) and `AppContainer` (the centralized dependency container). `AppContainer` instantiates and holds singletons for overlay management, Room database persistence (`AppDatabase`), calibration state ($K$), target distance threshold settings, and maintains the reactive `SpikeMetrics` `StateFlow` consumed by both the UI and background components.

### `com.visionguard.policy`
This package contains pure Kotlin domain logic with zero Android framework imports and time abstracted via a `Clock` interface for deterministic testing. In Phase 1, it houses:
- `Clock` and `TestClock`: Time abstraction interface.
- `FaceObservation`: Immutable representation of detected faces (width fraction, yaw angle, face count, timestamp).
- `EyeGuardPolicy`: Full Eye Guard distance estimation engine integrating pinhole geometry ($d \approx K / \text{widthFraction}$), user calibration ($K = d_{\text{cal}} \times w_{\text{cal}}$), Exponential Moving Average (EMA) smoothing ($\alpha = 0.35$), $N = 5$ consecutive-frame confirmation to prevent flicker, hysteresis (~20 cm trigger, ~30 cm recovery), proportional dimming opacity capped at 0.8, and 5-second no-face idle power saving. Fully covered by fast JVM unit tests (`EyeGuardPolicyTest`).

### `com.visionguard.data`
This package manages local offline Room database persistence. In Phase 1, it houses `EyeGuardEventEntity`, `EyeGuardEventDao`, and `AppDatabase`. Derived events (`TOO_CLOSE`, `RECOVERED`, `CALIBRATION`, `PAUSED`, `RESUMED`, `NO_FACE_POWER_SAVING`) are stored locally on-device without network transmission, providing an append-only safety event ledger observable as reactive coroutine Flows.

### `com.visionguard.overlay`
This package manages system-level alert and dimming overlays using Android's `WindowManager`. In Phase 1, `SpikeOverlayManager` displays a semi-transparent black view using `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`. It dynamically modulates opacity proportionally with closeness (scaling from 0.45 to a strict maximum cap of 0.80) to preserve underlying touch pass-through in accordance with Android 12+ security rules.

### `com.visionguard.vision`
This package manages camera capture and machine vision processing. `CameraForegroundService` extends `LifecycleService` with `foregroundServiceType="camera"` and displays a persistent foreground notification with interactive "Pause Protection" / "Resume Protection" controls. It streams CameraX front-camera frames to ML Kit's offline face detector (`PERFORMANCE_MODE_FAST`) without requiring eye landmarks. It maps detections into `FaceObservation`, evaluates `EyeGuardPolicy`, logs safety events to Room, controls proportional overlay dimming, throttles to 1 fps whenever no face is detected for $> 5$ seconds, and unbinds/rebinds camera hardware on `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON`.

### `com.visionguard.ui`
This package contains the Jetpack Compose user interface. In Phase 1, `MainActivity` renders `EyeGuardHomeScreen`, featuring:
- One-tap Start/Stop protection toggle and in-app Pause/Resume button.
- Prominent non-punitive warning banner when the phone is held too close.
- Distance Calibration card allowing one-tap calibration at ~30 cm reading distance.
- Threshold setting chips (20 cm default, 25 cm, 30 cm).
- Live Detection Metrics HUD (face count, raw and EMA width fraction, estimated distance in cm, consecutive close frames, overlay opacity, and analysis rate).
- Local Safety Event Ledger displaying recent Room database records.
- Permissions checklist with direct links to system settings.

### `com.visionguard.usage`
*(Planned for Phase 3)* Will house local foreground screen time aggregation using `UsageStatsManager` events (`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`).
