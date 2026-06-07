# EasyTimer

EasyTimer 是一个极简 Android 定时关闭工具。选择一个应用，设置倒计时，到时间后通过 Root 执行 `am force-stop`，强制关闭目标应用。

它的目标很简单：打开就能用，几步完成，没有账号、广告、云同步或统计。

## 功能

- 浏览已安装应用，显示图标、应用名和包名
- 搜索应用
- 收藏常用应用
- 记录最近选择的应用
- 使用 5、10、30、60 分钟预设
- 输入自定义分钟数
- 前台服务倒计时
- 通知栏暂停、继续、加 5 分钟、取消、立即关闭
- Quick Settings Tile 快捷入口
- Root 状态检测
- 定时结束后可自动关闭 EasyTimer 自身

## 使用前提

核心关闭能力依赖 Root：

```bash
su -c am force-stop <package>
```

如果设备没有 Root，应用可以打开和设置倒计时，但无法可靠强制关闭其他应用。

## 权限

项目当前声明了这些主要权限：

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- `QUERY_ALL_PACKAGES`
- `PACKAGE_USAGE_STATS`
- `RECEIVE_BOOT_COMPLETED`

其中通知权限在 Android 13 及以上会在启动时请求。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX DataStore
- MVVM
- Foreground Service
- Quick Settings Tile
- Gradle Kotlin DSL

## 项目结构

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
+-- viewmodel/
    +-- MainViewModel.kt
```

## 构建

Debug APK：

```powershell
.\gradlew.bat assembleDebug
```

Release APK：

```powershell
.\gradlew.bat assembleRelease
```

连接设备运行测试：

```powershell
.\gradlew.bat :app:connectedAndroidTest
```

如果依赖下载较慢或失败，可以先配置本地代理：

```powershell
$env:HTTP_PROXY='http://127.0.0.1:7890'
$env:HTTPS_PROXY='http://127.0.0.1:7890'
.\gradlew.bat assembleDebug
```

## 使用流程

1. 打开 EasyTimer。
2. 搜索或选择一个应用。
3. 选择预设时间，或输入自定义分钟数。
4. 点击开始计时，或点击启动并计时。
5. 倒计时结束后，EasyTimer 会尝试通过 Root 强制关闭目标应用。

## 持久化数据

应用使用 DataStore 保存：

- 收藏应用包名
- 最近使用应用包名
- 上次使用的计时时长
- 自毁模式开关

## 设计原则

- 极简
- 暗色模式友好
- 尽量少点击
- 不联网
- 不广告
- 不账号
- 不统计
- 不打扰用户

## 注意

Root 命令具有较高权限，请只在自己的设备上使用。不同 Android ROM 对后台服务、通知权限和 Root 授权弹窗的处理可能不同，首次使用建议先用一个无关紧要的应用测试关闭效果。
