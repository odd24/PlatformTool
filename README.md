# PlatformTool

PlatformTool（平台硬件工具）是一款面向 Android 工程设备、系统调试和硬件验证场景的工具应用。它将媒体测试、串口收发、传感器监测、日志查看、屏幕 FPS、快捷硬件开关和电池记录集中在一个应用中。

## 主要功能

- **音频播放**：选择并播放设备上的音频文件
- **视频测试**：浏览视频文件并进行普通或全屏播放
- **串口工具**：扫描串口设备，配置波特率，以文本或十六进制模式收发和导出数据
- **传感器监测**：独立查看加速度计、陀螺仪、磁力计、光线和距离传感器，并提供重力球演示
- **日志查看**：筛选、暂停、自动滚动和导出 Android 日志；通过桥接脚本读取内核日志
- **FPS 测试**：显示屏幕实时帧率并提供绘制测试场景
- **快捷开关**：控制触摸点/指针位置、手电筒、振动和屏幕常亮
- **电池信息**：查看电量、电流、电压、温度和供电状态，后台记录 CSV 并绘制趋势图

## 环境要求

- Android 6.0（API 23）或更高版本
- Android Studio 与 JDK 17
- Android SDK 34
- Gradle 8.7（项目已包含 Gradle Wrapper）
- 部分工程功能需要 ADB、Root、系统签名或特权应用权限

## 构建与安装

使用 Android Studio 打开项目并运行 `app`，或在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

默认应用 ID：

```text
com.example.platformtool
```

## 日志访问权限

普通应用不能直接读取完整的 Android 系统日志。连接已启用 USB 调试的设备后，可以运行：

```powershell
.\grant-log-access.bat
```

该脚本通过 ADB 授予 `android.permission.READ_LOGS` 并重启应用。部分设备仍要求 Root、工程系统或将应用安装为特权系统应用。

## 内核日志与快捷工具桥接

先安装 Debug APK并连接设备，然后运行：

```powershell
.\start-kernel-bridge.bat
```

或使用 PowerShell：

```powershell
.\start-kernel-bridge.ps1
```

脚本会把 `tools/platformtool-kernel-bridge.sh` 推送到设备，尝试通过 Root 或 ADB Shell 持续采集 `dmesg`，同时为受限系统上的触摸调试开关提供桥接。

停止桥接：

```powershell
.\stop-kernel-bridge.bat
```

或：

```powershell
.\stop-kernel-bridge.ps1
```

## 权限与兼容性说明

- 手电筒功能需要相机权限，且设备必须配备闪光灯。
- Android 13+ 的后台电池记录需要通知权限。
- 串口设备路径及读写权限取决于设备内核、SELinux 策略和系统配置。
- `READ_LOGS`、`WRITE_SECURE_SETTINGS` 和内核日志通常不是普通第三方应用可获得的权限。
- Root 与桥接功能应只在你拥有并获准调试的设备上使用。

## 项目结构

```text
app/                         Android 应用模块
tools/                       设备端桥接脚本
grant-log-access.bat         授予系统日志访问权限
start-kernel-bridge.*        启动内核日志与快捷工具桥接
stop-kernel-bridge.*         停止桥接
```

## 注意

本项目主要用于工程调试和硬件验证。不同 Android 厂商对系统权限、串口节点、Root 命令及日志访问的限制不同，具体可用功能以目标设备为准。
