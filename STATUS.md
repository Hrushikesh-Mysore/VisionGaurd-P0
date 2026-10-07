# VisionGuard Status

## Current Phase
**Pre-Phase 0: Environment Preflight & Project Initialization**

## What is Completed
- Ingested and verified specification from `docs/SRS.pdf` (`docs/SRS.txt`) and `docs/Agent.md`.
- Extracted `AGENTS.md` to repository root per Master Prompt specification.
- Initialized Git repository (`main` branch) and committed baseline setup (`18ea6c1`).
- Established tracking documentation: `STATUS.md`, `CHANGELOG.md`, `CODE_TOUR.md`, `HANDOFF.md`.
- Performed detailed environment toolchain check.

## What is Currently Being Worked On
- Environment toolchain readiness (resolving missing JDK, Android SDK, adb, and device connection).

## What is Not Completed
- Phase 0: Risk spike (camera foreground service, ML Kit face detection, overlay pass-through).
- Phase 1: Eye Guard + Home screen.
- Phase 2: Privacy Guard.
- Phase 3: Smart Dashboard.
- Phase 4: Profiles + PIN.
- Phase 5: Time Tokens.
- Phase 6: Privacy Ledger.
- Phase 7: Polish and APK.
- Phase 8: Event pack.

## Environment Check Details
1. **Java / JDK**: Not found (`which java` / `which javac` returned not found; `JAVA_HOME` unset; `/usr/lib/jvm` does not exist).
2. **Android SDK**: Not found (`ANDROID_HOME` / `ANDROID_SDK_ROOT` unset; `~/Android/Sdk` and `/usr/lib/android-sdk` do not exist).
3. **adb**: Not found (`which adb` returned not found).
4. **Gradle / Wrapper**: Not found (`which gradle` not found; project `./gradlew` not yet scaffolded).
5. **Connected Android Device**: None detected via USB (`lsusb` shows peripheral webcam and mouse; no phone detected).

## Known Bugs / Problems / Blockers
- **Build Environment Missing**: Missing JDK (17 or 21), Android SDK (commandline-tools / platforms / build-tools), and `adb`.
- **Target Device**: No Android device with USB debugging currently connected.

## Tests Performed and Results
- Toolchain environment probes executed (all reported above).

## Build Status
- Not built yet (waiting for toolchains to scaffold the project).

## APK / Device Testing Status
- Not deployed yet.

## Exact Next Recommended Action
- User to run manual setup commands to install JDK 17 (or 21), `adb`, Android SDK / Studio, and connect an Android device with USB debugging enabled.
- Verify environment with `java -version`, `adb devices`, and `echo $ANDROID_HOME`.
- Then instruct agent to scaffold the project and start Phase 0.

## Important Decisions Made
- Architecture strictly adheres to zero-network manifest constraint (`INTERNET` and `ACCESS_NETWORK_STATE` prohibited).
- No Hilt or complex DI; simple `AppContainer` pattern will be used.
- Single `:app` module layout (`vision`, `overlay`, `usage`, `data`, `policy`, `ui`).
