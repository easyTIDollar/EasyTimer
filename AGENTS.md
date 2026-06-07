# AGENTS.md

> EasyTimer 是一个极简 Android 工具，用于给指定应用设置倒计时，并在时间结束后通过 Root 强制关闭目标应用。

## 协作规则

- 默认使用中文回复。
- 遇到网络下载或依赖解析问题时，优先尝试本地代理 `127.0.0.1:7890`。
- 不要引入广告、账号、云同步、统计、社交等与核心目标无关的功能。
- 修改代码时保持 Kotlin 文件为 UTF-8 无 BOM。
- 优先保持应用“小、快、直接”，尽量保证核心流程 3 次点击内完成。

## 项目概览

- 包名：`com.jussicodes.easytimer`
- 语言：Kotlin
- UI：Jetpack Compose + Material 3
- 架构：MVVM + Repository Pattern
- Activity：单 Activity，手动页面路由
- 最低 SDK：26
- 目标 SDK：34
- 构建：Gradle Kotlin DSL

## 核心功能

1. 应用选择：读取已安装应用，显示图标、名称、包名，支持搜索、收藏和最近使用。
2. 定时设置：支持 5、10、30、60 分钟预设，也支持自定义分钟数。
3. 前台倒计时：通过 Foreground Service 保活，并在通知中显示倒计时。
4. 通知操作：支持暂停、继续、加 5 分钟、取消、立即关闭。
5. Root 强制关闭：通过 `su -c am force-stop <package>` 结束目标应用。
6. 快捷设置磁贴：QS Tile 可查看状态，运行中点击取消，空闲时打开应用。
7. 自毁模式：定时结束后可自动强制关闭 EasyTimer 自身。

## 目录结构

```text
app/src/main/java/com/jussicodes/easytimer/
+-- MainActivity.kt
+-- data/
|   +-- PreferencesRepository.kt
+-- model/
|   +-- Models.kt
+-- root/
|   +-- RootShellManager.kt
+-- service/
|   +-- TimerForegroundService.kt
|   +-- QuickTimerTileService.kt
+-- ui/
|   +-- screens/
|   |   +-- HomeScreen.kt
|   |   +-- TimerSetupScreen.kt
|   |   +-- ActiveTimerScreen.kt
|   |   +-- SettingsScreen.kt
|   +-- theme/
|       +-- Color.kt
|       +-- Theme.kt
|       +-- Type.kt
+-- viewmodel/
    +-- MainViewModel.kt
```

## 关键文件职责

| 文件 | 说明 |
| --- | --- |
| `app/build.gradle.kts` | Android、Compose、DataStore、Material 3 依赖配置 |
| `app/src/main/AndroidManifest.xml` | 权限、Activity、前台服务、QS Tile 声明 |
| `MainActivity.kt` | 入口 Activity，请求通知权限并挂载 Compose UI |
| `MainViewModel.kt` | 页面状态、应用列表、搜索、收藏、最近使用、计时器操作 |
| `PreferencesRepository.kt` | DataStore 持久化收藏、最近使用、上次时长、自毁开关 |
| `RootShellManager.kt` | Root 检测与 `am force-stop` 命令执行 |
| `TimerForegroundService.kt` | 倒计时、通知、暂停继续、加时、取消、强制关闭 |
| `QuickTimerTileService.kt` | 快捷设置磁贴状态与点击行为 |

## 数据流

```text
MainActivity
  -> AppContent()
     -> MainViewModel
        -> HomeScreen
        -> TimerSetupScreen
        -> ActiveTimerScreen
        -> SettingsScreen

MainViewModel
  -> PreferencesRepository
  -> TimerForegroundService.timerState
  -> RootShellManager.isRootAvailable()

TimerForegroundService
  -> CountDownTimer
  -> Notification
  -> RootShellManager.forceStopApp()
```

## DataStore 数据

| Key | 类型 | 说明 |
| --- | --- | --- |
| `favorite_packages` | `Set<String>` | 收藏应用包名 |
| `recent_packages` | `Set<String>` | 最近使用应用包名，最多 10 个 |
| `last_duration_minutes` | `Int` | 上次使用的计时时长 |
| `selfdestruct_enabled` | `Boolean` | 自毁模式开关 |

## 服务 Action

| Action | 行为 |
| --- | --- |
| `ACTION_START` | 开始新的倒计时 |
| `ACTION_PAUSE` | 暂停倒计时 |
| `ACTION_RESUME` | 继续倒计时 |
| `ACTION_CANCEL` | 取消倒计时 |
| `ACTION_FORCE_STOP_NOW` | 立即强制关闭目标应用 |
| `ACTION_ADD_5_MIN` | 当前倒计时增加 5 分钟 |

## 构建命令

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat :app:connectedAndroidTest
```

如果 Gradle 或依赖下载失败，先尝试：

```powershell
$env:HTTP_PROXY='http://127.0.0.1:7890'
$env:HTTPS_PROXY='http://127.0.0.1:7890'
.\gradlew.bat assembleDebug
```

## 开发注意事项

- 页面路由由 `Screen` enum 控制，不使用 Navigation 库。
- Root 检测和强制关闭必须放到后台线程，避免阻塞 UI。
- 前台服务需要及时调用 `startForeground()`，Android 14 需匹配 `specialUse` 类型。
- 通知入口、QS Tile、前台页面都应复用同一套 Service action。
- 自毁模式只由用户设置控制，启动计时后不要自动重置。
- 保持 UI 简洁，优先暗色模式友好，不增加复杂 onboarding。
- 不要把 Root 能力包装成普通无 Root 也可靠可用的能力；无 Root 时应明确提示能力受限。
