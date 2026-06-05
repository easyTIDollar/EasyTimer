# Android 定时关闭指定应用工具 - Claude Code 开发说明（Kotlin + Root）

---

````md
# 项目名称

EasyTimer

---

# 项目目标

开发一个 Android 应用（Kotlin），功能是：

> 定时强制关闭指定应用。

要求：

- 极简
- 即用即走
- 不要复杂功能
- 不做社交
- 不做账号系统
- 不做云同步
- 不做统计
- 不做广告
- 打开即可使用

我已经拥有：

- Android Root 权限
- Magisk
- Shizuku 可选（但优先 root）

目标是：

> 替代那些难用、广告多、限制多、操作复杂的“定时关闭”软件。

---

# 技术栈要求

- Kotlin
- Jetpack Compose（优先）
- minSdk >= 26
- targetSdk 最新稳定版
- Material 3
- 单 Activity 架构
- 不要使用 Flutter
- 不要 React Native

---

# 核心功能

## 1. 选择应用

显示已安装应用列表：

- 应用图标
- 应用名称
- 包名

支持：

- 搜索
- 收藏
- 最近使用

点击即可选中。

---

## 2. 设置定时关闭

用户可以：

- 输入分钟数
- 或选择：
  - 5 分钟
  - 10 分钟
  - 30 分钟
  - 1 小时

支持：

- 倒计时显示
- 暂停
- 取消

---

## 3. 到时间后强制关闭应用（核心）

优先使用 root：

```bash
su -c am force-stop 包名
````

如果 root 不可用：

备用方案：

```bash
am force-stop 包名
```

或者：

* Shizuku 支持（后续可扩展）

必须真正关闭后台进程。

---

# UI 风格要求

整体风格：

* 极简
* 类似系统工具
* 深色模式友好
* 操作步骤尽可能少

首页布局建议：

[搜索框]
[应用列表]

点击应用后：

[应用信息]
[快捷时间按钮]
[自定义时间输入]
[开始按钮]

底部：

[当前正在计时的任务]

---

# 必须实现的体验细节

## 1. 一键启动

用户操作流程：

* 打开 App
* 点应用
* 点时间
* 开始

最多 3 次点击完成。

---

## 2. 前台服务

使用 Foreground Service：

* 防止系统杀死
* 显示通知
* 展示倒计时

通知按钮：

* 取消
* 立即关闭

---

## 3. 开机恢复（可选）

如果用户开启：

* 重启后恢复未完成任务

---

## 4. 多任务支持（可选）

后期支持：

* 同时定时关闭多个应用

当前版本先做单任务。

---

# Root 实现要求

封装 RootShellManager：

功能：

```kotlin
fun executeRootCommand(cmd: String): Boolean
```

支持：

* 检测 root
* 执行命令
* 返回结果
* 超时处理

关闭应用逻辑：

```kotlin
su -c am force-stop <packageName>
```

---

# 架构要求

使用：

* MVVM
* Repository Pattern

目录结构：

```text
ui/
service/
root/
data/
model/
viewmodel/
```

---

# 数据存储

使用：

* DataStore

保存：

* 收藏应用
* 最近定时
* 用户设置

---

# 通知要求

通知内容：

```text
将在 15 分钟后关闭：
YouTube

剩余时间：14:32
```

按钮：

* 取消
* 立即关闭

---

# 权限

需要：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

不要申请无关权限。

---

# 希望 Claude Code 完成的内容

请直接生成：

1. 完整 Android Studio 项目
2. Gradle 配置
3. Compose UI
4. Foreground Service
5. Root 执行模块
6. App 列表读取
7. 定时逻辑
8. Notification
9. DataStore
10. 可直接运行的代码

---

# 代码要求

* 不要只给示例代码
* 不要省略文件
* 不要伪代码
* 所有 import 完整
* 所有类完整
* 保证可编译

---

# 额外优化（如果可以）

## 自动检测前台应用

增加：

```kotlin
UsageStatsManager
```

实现：

* 当某应用启动超过 X 分钟后自动关闭

例如：

* 抖音使用 30 分钟自动关闭

---

## 悬浮窗快捷关闭（后续）

未来可扩展：

* 悬浮倒计时球
* 一键终止当前应用

---

# 我想要的最终效果

这个工具应该像：

* 系统工具
* 小而快
* 没广告
* 不联网
* 不打扰用户

重点是：

> “打开就能用”
> “三步完成”
> “真正强制关闭应用”

```

你还可以让 Claude Code 继续帮你做：

- Magisk 模块版
- Shizuku 无 Root 版
- Compose 动效优化
- MIUI/OneUI 后台保活适配
- 悬浮窗倒计时
- 自动关闭短视频 App
- 使用时长限制系统
- 黑白名单机制
- Accessibility 自动检测前台应用
- Material You 动态取色
```
