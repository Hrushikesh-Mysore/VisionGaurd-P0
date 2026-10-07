# VisionGuard Code Tour

## Architecture Overview
VisionGuard is implemented as a single `:app` module Android application built with Jetpack Compose, Material 3, Coroutines/StateFlow, Room with KSP, CameraX, and bundled offline ML Kit Face Detection. The application is strictly offline and privacy-first (no network permissions declared). Dependency injection is handled cleanly via a lightweight `AppContainer` singleton rather than third-party DI frameworks.

## Package Tour

### `com.visionguard`
The root package contains `VisionGuardApp` (the custom `Application` class) and `AppContainer` (the centralized dependency container). `AppContainer` instantiates and holds singletons for overlay management, Room database persistence (`AppDatabase`), calibration state ($K$), target distance threshold settings, Privacy Guard toggle state, and maintains the reactive `SpikeMetrics` `StateFlow` consumed by both the UI and background components.

### `com.visionguard.policy`
This package contains pure Kotlin domain logic with zero Android framework imports and time abstracted via a `Clock` interface for deterministic testing.
- `Clock` and `TestClock`: Time abstraction interface.
- `FaceObservation` & `DetectedFace`: Immutable representation of detected faces (owner width fraction, yaw angle, total face count, timestamp, and list of secondary faces).
- `EyeGuardPolicy`: Full Eye Guard distance estimation engine integrating pinhole geometry ($d \approx K / \text{widthFraction}$), user calibration ($K = d_{\text{cal}} \times w_{\text{cal}}$), Exponential Moving Average (EMA) smoothing ($\alpha = 0.35$), $N = 5$ consecutive-frame confirmation to prevent flicker, hysteresis (~20 cm trigger, ~30 cm recovery), proportional dimming opacity capped at 0.8, and 5-second no-face idle power saving. Fully covered by JVM unit tests (`EyeGuardPolicyTest`).
- `PrivacyGuardPolicy`: Phase 2 secondary viewer detection engine. Identifies secondary faces with `widthFraction >= 0.10` and `abs(yaw) < 35°` facing the screen, requiring $N = 3$ consecutive frames to trigger an alert. Automatically clears after 2 seconds of absence. Supports manual dismissal. Unit tested in `PrivacyGuardPolicyTest`.
- **Guard Priority Rule**: Privacy Guard shield takes precedence over Eye Guard dimming. Unauthorized secondary viewing is an urgent confidentiality breach requiring immediate obscuring of screen contents, which supersedes personal ergonomic viewing distance dimming.

### `com.visionguard.data`
This package manages local offline Room database persistence via `EyeGuardEventEntity`, `EyeGuardEventDao`, and `AppDatabase`. Derived events (`TOO_CLOSE`, `RECOVERED`, `CALIBRATION`, `PAUSED`, `RESUMED`, `NO_FACE_POWER_SAVING`, `PRIVACY_ALERT`, `PRIVACY_RECOVERED`, `PRIVACY_DISMISSED`, `PRIVACY_TOGGLED`) are stored locally on-device without network transmission, providing an append-only safety event ledger observable as reactive coroutine Flows.

### `com.visionguard.overlay`
This package manages system-level alert and dimming overlays using Android's `WindowManager`. `SpikeOverlayManager` manages two distinct visual protection modes:
1. `EYE_GUARD_DIM`: Semi-transparent black view modulating opacity proportionally with closeness (scaling from 0.45 to a strict maximum cap of 0.80).
2. `PRIVACY_GUARD_SHIELD`: Frosted dark navy slate overlay (0.80 alpha). On API 31+ devices with hardware cross-window blur enabled, dynamically activates `FLAG_BLUR_BEHIND` with `blurBehindRadius`.
- **Trade-Off Decision**: Overlays use `FLAG_NOT_TOUCHABLE` to remain click-through, ensuring users are never locked out of underlying tasks or device controls. Because click-through overlays cannot directly consume touch taps, dismissal controls are provided externally via persistent notification actions and in-app buttons.

### `com.visionguard.vision`
This package manages camera capture and machine vision processing. `CameraForegroundService` extends `LifecycleService` with `foregroundServiceType="camera"`. It streams CameraX front-camera frames to ML Kit's offline face detector (`PERFORMANCE_MODE_FAST`). It maps detections into `FaceObservation` (distinguishing the largest face as owner and remaining faces as potential secondary viewers), concurrently evaluates `EyeGuardPolicy` and `PrivacyGuardPolicy`, coordinates priority overlays, logs safety events to Room, throttles to 1 fps during extended no-face periods, and unbinds/rebinds camera hardware on screen off/on.

### `com.visionguard.ui`
This package contains the Jetpack Compose user interface. `MainActivity` renders `VisionGuardHomeScreen`, featuring:
- One-tap Start/Stop protection toggle and in-app Pause/Resume button.
- Prominent Privacy Alert banner when secondary viewers are detected, with one-tap "Dismiss Privacy Shield" control.
- Eye Guard warning banner when the phone is held too close.
- Privacy Guard on/off toggle switch.
- Distance Calibration card allowing one-tap calibration at ~30 cm reading distance.
- Threshold setting chips (20 cm default, 25 cm, 30 cm).
- Live Multi-Face Detection Metrics HUD (total faces, secondary viewers, confirmation frames, privacy state, owner face width, estimated distance, eye guard state, active overlay mode).
- Local Safety Event Ledger displaying recent Room database records.
- Permissions checklist with direct links to system settings.

### `com.visionguard.usage`
*(Planned for Phase 3)* Will house local foreground screen time aggregation using `UsageStatsManager` events (`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`).
