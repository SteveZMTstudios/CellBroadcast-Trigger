# CellBroadcast Trigger

[简体中文](README_zh.md) | [English](README.md)

This is a testing tool for simulating and triggering Wireless Emergency Alerts (WEA) and Earthquake & Tsunami Warning Systems (ETWS) on Android devices.

This application uses root privileges to call hidden APIs at the Android system level, directly sending fake cell broadcast messages to the system broadcast receiver. This is extremely useful for testing the phone's alarm reception capabilities, UI display, and for developing and debugging related applications.

## Real World Usage
It found a real [android design bugs](https://issuetracker.google.com/issues/472754120).


### Compilation Environment

* **JDK**: Java 11 or later (project configured with `sourceCompatibility = JavaVersion.VERSION_11`).

* **Android SDK**:

* `compileSdk`: 36

* `minSdk`: 21 (Android 5.0)

* `targetSdk`: 36

* **Gradle**: The project includes a Gradle Wrapper; it is recommended to use the `./gradlew` command directly.

### Compilation Steps

1. Clone the project to your local machine.

2. Open the project root directory using Android Studio.

3. Sync the Gradle project.

4. Connect your device or launch the emulator.

5. Run the following command to install the debug package:

```bash

./gradlew installDebug

```
Or build the release package:

```bash

./gradlew assembleRelease

```

## Usage Instructions

For detailed test cases and troubleshooting, see the [Testing Guide](TESTING.md).

### Prerequisites

* Your Android device must **have root access** (Magisk, KernelSU, APatch, etc.).

* Grant the application root access (requested the first time you tap "TRIGGER ALERT").

### App Usage

1. Open the application.

2. Enter the alert message.

3. Select the alert level (e.g., "Presidential" or "ETWS: Earthquake").

4. (Optional) Set the delay time.

5. Click the **"TRIGGER ALERT (ROOT)"** button.

6. Observe whether the phone displays a system-level alert pop-up.

### Google Play Services (GMS) Earthquake Alerts (Xposed)

This app includes a specialized module to trigger Google's internal Earthquake Alert UI.

1. **Prerequisites**:
   - **LSPosed/EdXposed** installed.
   - Enable this app in the Xposed manager.
   - **Scope**: Ensure "Google Play Services" (`com.google.android.gms`) is selected in the scope.
   - Reboot your device.

2. **Usage**:
   - Expand the **"Google Play Services Alerts"** section.
   - Configure parameters:
     - **Region Name**: The city/region shown in the alert title.
     - **Epicenter (Lat/Lng)**: The simulated location of the earthquake.
     - **Distance**: Your simulated distance from the epicenter (affects the "Estimated Time of Arrival").
     - **Damage Radius**: Controls the size of the red/yellow intensity polygons on the map.
     - **Alert Type**: `1` for "Take Action" (Strong), `2` for "Be Aware" (Weak).
   - Check **"Simulate Real Alert"** to remove the "Test" prefix from the UI.
   - Click **"Trigger GMS Alert (Xposed)"**.

### Advanced Options

You can fine-tune the low-level CellBroadcast parameters:
- **Serial Number**: Unique ID for the message. Changing this allows the system to treat it as a new alert.
- **Service Category**: Override the 3GPP service category (e.g., 4352 for Earthquake).
- **Priority**: Message priority (0-3).
- **Geo Scope**: Geographical scope (Cell-wide, PLMN-wide, etc.).
- **DCS**: Data Coding Scheme for character encoding.
- **Slot Index**: Target SIM slot (0 for SIM1, 1 for SIM2).

### ADB Command Line Usage (For ADB Root only)
If you cannot grant root via GUI (e.g., you only have `adb root`), use the following steps. For Windows compatibility, it is recommended to use variables:

1. **Get APK path and store in variable**:
   ```bash
   # Windows (PowerShell)
   $APK_PATH = adb shell "pm path top.stevezmt.cellbroadcast.trigger | cut -d: -f2"
   
   # Linux / macOS
   APK_PATH=$(adb shell "pm path top.stevezmt.cellbroadcast.trigger | cut -d: -f2")
   ```

2. **Execute trigger command**:
   ```bash
   # Format: adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain <Base64> <type> <delay> <isEtws> <serial> <category> <priority> <scope> <dcs> <slot> <lang>"
   
   # Example: Earthquake alert with serial 1234 and priority 3
   adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain '5Zyw6ZyH6aKE6K2m' 0 0 true 1234 -1 3 3 0 0 'zh'"
   ```

**Parameter Description (In order):**
1. `Base64 content`: UTF-8 string encoded in Base64.
2. `Type Code`: CMAS (0-4) or ETWS (0-4).
3. `Delay ms`: 0 for immediate.
4. `isEtws`: `true` or `false`.
5. `Serial`: 0-65535 (default 1234).
6. `Category`: Override value (default -1).
7. `Priority`: 0-3 (default 3).
8. `Geo Scope`: 0-3 (default 3).
9. `DCS`: Data Coding Scheme (default 0).
10. `Slot Index`: 0 or 1.
11. `Language`: e.g., 'zh' or 'en'.

### ADB Command Line Usage (Legacy Simple Format)
If you don't need advanced options:
```bash
adb shell "CLASSPATH=$APK_PATH app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain '<Base64>' <type> <delay> <isEtws>"
```

## Disclaimer

**Please use this tool responsibly.**

* This application is for **development, testing, and research** purposes only.

* **It is strictly forbidden to use this application to trigger alarms in public places** or in situations that may cause misunderstanding or panic among others.

* The simulated alarm sound and vibration are completely identical to a real alarm and may cause disturbance or fright to those around you.

* The developer is not responsible for any consequences resulting from the misuse of this tool.

## License

[GPL v3](LICENSE)