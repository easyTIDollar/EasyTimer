# AGENTS.md

> EasyTimer - a minimal Android tool for scheduled app closing.

## Project Overview

- **Package**: `com.jussicodes.easytimer`
- **Language**: Kotlin
- **Architecture**: MVVM + Repository Pattern
- **UI**: Jetpack Compose (single Activity)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build**: Gradle Kotlin DSL (AGP 8.5.0, Kotlin 1.9.0)

## Core Features

1. **App Selection** - Browse installed apps (icon, name, package), search, favorites, recent sort
2. **Timer Setup** - 5/10/30/60 min presets or custom input; defaults to last used duration
3. **Foreground Countdown Service** - Foreground service + notification: pause/resume/cancel/force-stop/+5min
4. **Root Force Stop** - Kills target app via `su -c am force-stop <package>` on complete or user action
5. **Notification Actions** - pause/resume/+5min/cancel/force-stop
6. **QS Tile** - Quick Settings tile (show status/cancel/open app)
7. **Self-Destruct** - Auto-close EasyTimer after target killed (Root required), persisted state

## Directory Structure

`
app/src/main/java/com/jussicodes/easytimer/
+-- data/
|   +-- PreferencesRepository.kt       # DataStore (favorites, recent, last duration, self-destruct)
+-- model/
|   +-- Models.kt                       # AppInfo, Screen enum, TimerState sealed class
+-- root/
|   +-- RootShellManager.kt             # Root detection + su command execution
+-- service/
|   +-- TimerForegroundService.kt       # Foreground service: countdown, notification, root kill, +5min
|   +-- QuickTimerTileService.kt        # QS Tile
+-- ui/
|   +-- screens/
|   |   +-- HomeScreen.kt               # Home: app list + search + favorites + recent
|   |   +-- TimerSetupScreen.kt          # Timer setup: presets + custom
|   |   +-- ActiveTimerScreen.kt         # Active timer: progress ring + controls
|   |   +-- SettingsScreen.kt            # Settings: self-destruct + Root status
|   +-- theme/
|       +-- Color.kt, Theme.kt, Type.kt
+-- viewmodel/
|   +-- MainViewModel.kt                # State management + business logic
+-- MainActivity.kt                     # Entry Activity (4 Screen routes)
`

## Key Files

| File | Responsibility |
|---|---|
| `app/build.gradle.kts` | Compose BOM, DataStore, Material 3 |
| `app/src/main/AndroidManifest.xml` | Permissions: FOREGROUND_SERVICE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, QUERY_ALL_PACKAGES |
| `MainViewModel.kt` | App list, search/filter, favorites/recent, timer actions, self-destruct persistence |
| `TimerForegroundService.kt` | CountDownTimer, notification, root kill, +5min action |
| `RootShellManager.kt` | Singleton, isRootAvailable() + forceStopApp() |
| `PreferencesRepository.kt` | DataStore: favorites, recent (max 10), last duration, self-destruct |

## Data Flow

`
MainActivity
  +-- AppContent() --> MainViewModel
          +-- HomeScreen        Search/favorites/app selection
          +-- TimerSetupScreen  Duration (defaults to last used)
          +-- ActiveTimerScreen Countdown controls
          +-- SettingsScreen    Self-destruct toggle

MainViewModel
  +-- filteredApps (StateFlow) <-- combine(all apps, query, favorites, recent)
  +-- timerState (StateFlow)   <-- TimerForegroundService._timerState
  +-- selectedPreset (StateFlow) <-- init 0, restored from lastDuration
  +-- selfDestruct (StateFlow) <-- DataStore restore/set

TimerForegroundService
  +-- ACTION_START         --> Start countdown
  +-- ACTION_PAUSE         --> Pause
  +-- ACTION_RESUME        --> Resume
  +-- ACTION_CANCEL        --> Cancel
  +-- ACTION_FORCE_STOP_NOW --> Root kill + notification
  +-- ACTION_ADD_5_MIN     --> Extend by 5 min (+300s)
`

## Data Persistence

| DataStore Key | Type | Description |
|---|---|---|
| `favorite_packages` | Set<String> | Favorited package names |
| `recent_packages` | Set<String> | Recent package names (max 10) |
| `last_duration_minutes` | Int | Last timer duration in minutes |
| `selfdestruct_enabled` | Boolean | Self-destruct toggle |

## Development Notes

- **Single Activity routing**: 4 Composables via `Screen` enum (HOME/TIMER_SETUP/ACTIVE_TIMER/SETTINGS), no Navigation lib
- **File encoding**: All `*.kt` files must be **UTF-8** (no BOM)
- **Default timer**: `_selectedPreset` init = 0, restored from `lastDuration` on start
- **Self-destruct**: `toggleSelfDestruct()` writes DataStore, NOT reset after timer start
- **Root detection**: `su -c echo root_ok`, non-blocking
- **Foreground service**: Android 14 constraint, ViewModel catches `ForegroundServiceStartNotAllowedException`
- **Design**: Minimal, dark-mode friendly, max 3 taps

## Build Commands

`powershell
.\gradlew assembleDebug          # Debug APK
.\gradlew assembleRelease        # Release APK
.\gradlew :app:connectedAndroidTest  # Instrumented tests
`

## Code Conventions

- `kotlin.code.style=official`
- Namespace: `com.jussicodes.easytimer`
- Manual Composable routing, no third-party navigation
- No Flutter/React Native, no social/accounts/cloud/analytics/ads
