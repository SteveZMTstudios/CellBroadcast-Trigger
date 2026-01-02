# CellBroadcast Trigger

[简体中文](README_zh.md) | [English](README.md)

这是一个用于在 Android 设备上模拟和触发无线紧急警报 (Wireless Emergency Alerts, WEA) 和地震海啸预警系统 (ETWS) 的测试工具。

本应用通过 Root 权限调用 Android 系统底层的隐藏 API，直接向系统广播接收器发送伪造的小区广播消息。这对于测试手机的警报接收功能、UI 展示以及相关应用的开发调试非常有用。
 
### 编译环境

*   **JDK**: Java 11 或更高版本 (项目配置为 `sourceCompatibility = JavaVersion.VERSION_11`)。
*   **Android SDK**:
    *   `compileSdk`: 36
    *   `minSdk`: 21 (Android 5.0)
    *   `targetSdk`: 36
*   **Gradle**: 项目包含 Gradle Wrapper，建议直接使用 `./gradlew` 命令。

### 编译步骤

1.  克隆项目到本地。
2.  使用 Android Studio 打开项目根目录。
3.  同步 Gradle 项目。
4.  连接设备或启动模拟器。
5.  运行以下命令安装调试包：

```bash
./gradlew installDebug
```

或者构建发布包：

```bash
./gradlew assembleRelease
```

## 使用说明

有关详细的测试用例和故障排查，请参阅 [测试指南](TESTING_zh.md)。

### 前置条件
*   您的 Android 设备必须**已获取 Root 权限** (Magisk, KernelSU, APatch 等)。
*   授予应用 Root 权限（首次点击 "TRIGGER ALERT" 时会请求）。

### App 端使用
1.  打开应用。
2.  输入警报消息内容。
3.  选择警报等级（如 "Presidential" 或 "ETWS: Earthquake"）。
4.  (可选) 设置延迟时间。
5.  点击 **"TRIGGER ALERT (ROOT)"** 按钮。
6.  观察手机是否弹出系统级警报弹窗。

### Google Play 服务 (GMS) 地震预警 (Xposed)

本应用包含一个专门的模块，用于触发 Google 内部的地震预警 UI。

1.  **前置条件**:
    - 已安装 **LSPosed/EdXposed**。
    - 在 Xposed 管理器中启用本应用。
    - **作用域**: 确保勾选了 "Google Play 服务" (`com.google.android.gms`)。
    - 重启手机。

2.  **使用方法**:
    - 展开 **"Google Play 服务警报"** 部分。
    - 配置参数：
        - **地区名称**: 显示在警报标题中的城市/地区。
        - **震中经纬度**: 模拟地震发生的位置。
        - **距震中距离**: 您与震中的模拟距离（影响“预计到达时间”）。
        - **震感半径**: 控制地图上红色/黄色烈度多边形的大小。
        - **警报类型**: `1` 代表“采取行动”（强震），`2` 代表“注意”（弱震）。
    - 勾选 **“模拟真实警报”** 可去除 UI 中的“演习”前缀。
    - 点击 **“触发 GMS 警报 (Xposed)”**。

### 高级选项

您可以微调底层小区广播参数：
- **序列号 (Serial Number)**: 消息的唯一 ID。更改此值可让系统将其视为新警报。
- **服务类别 (Service Category)**: 覆盖 3GPP 服务类别（例如地震为 4352）。
- **优先级 (Priority)**: 消息优先级 (0-3)。
- **地理范围 (Geo Scope)**: 地理范围（小区级、PLMN 级等）。
- **DCS**: 字符编码的数据编码方案。
- **卡槽索引 (Slot Index)**: 目标 SIM 卡槽（0 为卡 1，1 为卡 2）。

### ADB 命令行使用 (适用于仅有 ADB Root 的情况)
如果您无法通过 GUI 授予 Root 权限（例如仅有 `adb root`），可以使用以下命令。为了兼容 Windows 环境，建议分步执行：

1. **获取 APK 路径并存入变量**:
   ```bash
   # Windows (PowerShell)
   $APK_PATH = adb shell "pm path top.stevezmt.cellbroadcast.trigger | cut -d: -f2"
   
   # Linux / macOS
   APK_PATH=$(adb shell "pm path top.stevezmt.cellbroadcast.trigger | cut -d: -f2")

   # adb shell
   APK_PATH=$(pm path top.stevezmt.cellbroadcast.trigger | cut -d: -f2)
   ```

2. **执行触发命令**:
   ```bash
   # 格式：adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain <Base64内容> <类型> <延迟> <是否ETWS> <序列号> <类别> <优先级> <范围> <DCS> <卡槽> <语言>"
   
   # 示例：发送一条序列号为 1234，优先级为 3 的地震预警
   adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain '5Zyw6ZyH6aKE6K2m' 0 0 true 1234 -1 3 3 0 0 'zh'"
   ```

**参数说明 (按顺序):**
1. `Base64内容`: UTF-8 字符串的 Base64 编码。
2. `类型代码`: CMAS (0-4) 或 ETWS (0-4)。
3. `延迟毫秒`: 0 为立即。
4. `是否ETWS`: `true` 或 `false`。
5. `序列号`: 0-65535 (默认 1234)。
6. `服务类别`: 覆盖默认值 (默认 -1)。
7. `优先级`: 0-3 (默认 3)。
8. `地理范围`: 0-3 (默认 3)。
9. `数据编码 (DCS)`: 默认 0。
10. `卡槽索引`: 0 或 1。
11. `语言代码`: 如 'zh' 或 'en'。

### ADB 命令行使用 (旧版简易格式)
如果您不需要配置高级选项，可以使用简易格式：
```bash
adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain '<Base64内容>' <类型代码> <延迟毫秒> <是否ETWS>"
```

## 免责声明

**请务必负责任地使用此工具。**

*   本应用仅供**开发、测试和研究**目的使用。
*   **严禁在公共场合**或在可能引起他人误解、恐慌的情况下使用此应用触发警报。
*   模拟的警报声音和震动与真实警报完全一致，可能会对周围人群造成干扰或惊吓。
*   开发者不对因滥用此工具造成的任何后果负责。

## License

[GPL v3](LICENSE)
