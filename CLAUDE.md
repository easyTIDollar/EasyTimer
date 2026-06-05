# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the debug APK
./gradlew assembleDebug

# Build the release APK
./gradlew assembleRelease

# Run all unit tests (JVM, fast)
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.jussicodes.easytimer.ExampleUnitTest"

# Run connected Android instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint

# Clean build artifacts
./gradlew clean
```

## Architecture

This is an Android app that lets users set a timer to **force-stop a selected app** when the countdown expires. It uses **Kotlin** and **Jetpack Compose** with **Material 3**. Single-module project (`:app`).

- **Package**: `com.jussicodes.easytimer`
- **Min SDK**: 26, **Target/Compile SDK**: 34
- **Kotlin**: 1.9.0, **AGP**: 8.5.0, **Compose BOM**: 2024.04.01, **Compose Compiler**: 1.5.1

### Navigation

Screen-based navigation uses a simple `Screen` enum (`HOME`, `TIMER_SETUP`, `ACTIVE_TIMER`) driven by `MainViewModel.currentScreen: StateFlow<Screen>`. There is no Jetpack Navigation/NavHost — the `AppContent` composable in `MainActivity.kt` switches via a `when` block. The service also pushes screen transitions: when `TimerForegroundService.timerState` emits `Running` (not paused), the ViewModel forces `ACTIVE_TIMER`; when it emits `Idle` while on `ACTIVE_TIMER`, it returns to `HOME`.

### Key files

| File | Role |
|------|------|
| `MainActivity.kt` | Single activity. Requests notification permission on Android 13+, calls `enableEdgeToEdge()`, hosts Compose. `AppContent` wires navigation based on `MainViewModel.currentScreen`. |
| `viewmodel/MainViewModel.kt` | Central ViewModel extending `AndroidViewModel`. Manages all UI state (screens, search, app lists, timer). No factory — uses default `viewModel()` composable. Loads installed apps via `PackageManager`, filters to those with a launch intent. |
| `model/Models.kt` | `AppInfo` data class (packageName, appName, icon), `Screen` enum, `TimerState` sealed class (`Idle` / `Running`). |
| `data/PreferencesRepository.kt` | Persistence via Jetpack DataStore (`easytimer_prefs`). Stores favorite packages, recent packages (max 10, insertion-ordered), and last used duration. |
| `root/RootShellManager.kt` | Singleton wrapping `su -c` shell commands. Checks root availability and executes `am force-stop <pkg>`. |
| `service/TimerForegroundService.kt` | Foreground service running a `CountDownTimer`. On finish (or manual trigger), force-stops the target app via `RootShellManager` + a no-root fallback (`am force-stop` without `su`). Emits `TimerState` via a companion `StateFlow`. Posts a persistent notification with pause/resume/cancel/force-stop actions. |
| `service/QuickTimerTileService.kt` | Quick Settings tile. Tapping cancels an active timer, or opens `MainActivity` to start a new one. Reads `TimerForegroundService.timerState` to determine current state and refresh the tile label/active state. |
| `ui/screens/HomeScreen.kt` | Home with a favorites grid (3-column) and search mode. Search triggers `loadAllApps()` lazily. Favorites/recent apps are sorted above alphabetical results. |
| `ui/screens/TimerSetupScreen.kt` | Preset durations (5/10/30/60 min) as `FilterChip`s, custom minute input, and a start button. Displays last-used duration. Default preset is 15 min (not shown as a chip). |
| `ui/screens/ActiveTimerScreen.kt` | Circular progress with color transitions (green → orange at 5min → red at 1min), monospace countdown, pause/resume, cancel, and "force stop now" controls. |
| `ui/theme/Theme.kt` | `EasyTimerTheme` — dynamic color on Android 12+, manual dark/light fallback otherwise. |
| `ui/theme/Color.kt` | Color constants for the fallback color schemes. |
| `ui/theme/Type.kt` | Material 3 `Typography` (only `bodyLarge` overridden). |

### Dependency management

Dependencies are declared in `gradle/libs.versions.toml`. When adding dependencies, add them to the TOML and reference via `libs.<alias>` rather than hardcoding Maven coordinates.

### Test conventions

- **Unit tests** (`app/src/test/`): JVM-hosted JUnit 4. No Android framework available.
- **Instrumented tests** (`app/src/androidTest/`): Run on device/emulator, `AndroidJUnit4` runner. Compose UI testing deps pre-configured.

### Permissions and requirements

The app requires **root access** for core functionality. The service also attempts a no-root `am force-stop` as fallback. Permissions declared in the manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS` (for the Quick Settings tile), and `QUERY_ALL_PACKAGES`.
