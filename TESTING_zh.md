# 测试指南与测试用例

本文档提供了 CellBroadcast Trigger 应用的全面测试用例。

## 1. 环境要求

| 要求 | 标准 Root (GUI) | GMS 警报 (Xposed) | ADB Root (命令行) |
| :--- | :---: | :---: | :---: |
| Root 权限 (Magisk/KernelSU) | 是 | 是 | 可选 |
| LSPosed/EdXposed | 否 | 是 | 否 |
| ADB Root 权限 | 可选 | 可选 | 是 |

---

## 2. 测试用例

### 用例 1: 标准 WEA/ETWS 触发 (GUI)
*   **目标**: 验证应用能否通过 Root 权限触发系统级警报。
*   **步骤**:
    1.  打开应用。
    2.  在文本框输入 "测试警报消息"。
    3.  选择 "Presidential" 或 "ETWS: Earthquake"。
    4.  点击 **"触发警报 (ROOT)"**。
*   **预期结果**:
    -   手机弹出系统级全屏警报或通知。
    -   播放特殊的紧急警报提示音。
    -   日志显示 "Process exited with code 0"。

### 用例 2: GMS 地震预警模拟 (Xposed)
*   **目标**: 验证 Xposed 模块是否正确 Hook GMS 并显示地震 UI。
*   **步骤**:
    1.  确保在 LSPosed 中启用了本应用，且作用域包含 "Google Play 服务"。
    2.  展开 **"Google Play 服务警报"**。
    3.  输入自定义 **地区名称** (如 "东京")。
    4.  设置 **经纬度** 和 **震感半径** (如 50km)。
    5.  勾选 **"模拟真实警报"**。
    6.  点击 **"触发 GMS 警报 (Xposed)"**。
*   **预期结果**:
    -   弹出 Google 全屏地震预警界面。
    -   地图根据半径显示红色/黄色圆圈。
    -   标题显示 "地震：东京" (没有“演习”前缀)。

### 用例 3: 仅通过 ADB Root 触发 (仅限命令行)
*   **目标**: 验证在 GUI 无法获取 Root 时，核心逻辑是否仍能通过 ADB 工作。
*   **步骤**:
    1.  将手机连接至电脑。
    2.  运行 `adb root`。
    3.  运行 README 中提供的命令 (替换路径和 Base64 内容):
        ```bash
        adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain 'VGVzdCBBbGVydA==' 0 0 false"
        ```
*   **预期结果**:
    -   即使应用 GUI 未获得 Root 权限，警报也能成功触发。
    -   适用于 `su` 二进制文件仅对 `adbd` 可用的设备。

### 用例 4: 高级参数验证
*   **目标**: 验证底层参数是否正确传递。
*   **步骤**:
    1.  展开 **"高级选项"**。
    2.  将 **序列号 (Serial Number)** 修改为新值 (如 5678)。
    3.  将 **卡槽索引 (SIM Slot Index)** 修改为 `1` (如果使用双卡手机的 SIM2)。
    4.  触发警报。
*   **预期结果**:
    -   警报在指定的 SIM 卡槽上接收。
    -   修改序列号后，即使刚刚关闭了类似的警报，系统也会再次显示。

### 用例 5: 错误处理 (无 Root)
*   **目标**: 验证应用在缺少权限时能正常处理。
*   **步骤**:
    1.  使用未 Root 的设备，或在 Magisk 中拒绝 Root 权限。
    2.  点击 **"触发警报 (ROOT)"**。
*   **预期结果**:
    -   弹出标题为“需要 Root 权限”的对话框。
    -   应用不会崩溃。

---

## 3. 常见问题排查

-   **警报未显示？**
    -   检查系统设置中是否开启了“无线紧急警报”。
    -   检查 LSPosed 管理器中的 Xposed 日志，看是否有 "Hook failed" 消息。
    -   确保 ADB 命令中的 `CLASSPATH` 正确 (使用 `pm path` 验证)。
-   **没有声音？**
    -   检查手机是否处于“勿扰模式” (虽然 Presidential 级别通常会绕过此模式)。
    -   在系统 WEA 设置中检查“警报提示音”设置。
