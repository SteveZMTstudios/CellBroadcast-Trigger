# Testing Guide & Test Cases

This document provides comprehensive test cases for the CellBroadcast Trigger application.

## 1. Environment Requirements

| Requirement | Standard Root (GUI) | GMS Alerts (Xposed) | ADB Root (CLI) |
| :--- | :---: | :---: | :---: |
| Root Access (Magisk/KernelSU) | Yes | Yes | Optional |
| LSPosed/EdXposed | No | Yes | No |
| ADB Root Access | Optional | Optional | Yes |

---

## 2. Test Cases

### Case 1: Standard WEA/ETWS Trigger (GUI)
*   **Goal**: Verify that the app can trigger system-level alerts using root.
*   **Steps**:
    1.  Open the app.
    2.  Enter "Test Alert Message" in the text field.
    3.  Select "Presidential" or "ETWS: Earthquake".
    4.  Click **"TRIGGER ALERT (ROOT)"**.
*   **Expected Result**:
    -   A system-level full-screen alert or notification appears.
    -   The distinctive emergency alert tone plays.
    -   Logs show "Process exited with code 0".

### Case 2: GMS Earthquake Alert Simulation (Xposed)
*   **Goal**: Verify the Xposed module correctly hooks GMS to show the earthquake UI.
*   **Steps**:
    1.  Ensure the app is enabled in LSPosed with "Google Play Services" in scope.
    2.  Expand **"Google Play Services Alerts"**.
    3.  Enter a custom **Region Name** (e.g., "Tokyo").
    4.  Set **Latitude/Longitude** and a **Damage Radius** (e.g., 50km).
    5.  Check **"Simulate Real Alert"**.
    6.  Click **"Trigger GMS Alert (Xposed)"**.
*   **Expected Result**:
    -   Google's full-screen earthquake alert UI appears.
    -   The map shows the red/yellow circles based on the radius.
    -   The title shows "Earthquake: Tokyo" (without "Test" prefix).

### Case 3: ADB Root Trigger (CLI Only)
*   **Goal**: Verify the core logic works via ADB when GUI root is unavailable.
*   **Steps**:
    1.  Connect phone to PC.
    2.  Run `adb root`.
    3.  Run the command provided in README (replace path and base64):
        ```bash
        adb shell "CLASSPATH=\$(pm path top.stevezmt.cellbroadcast.trigger | cut -d: -f2) app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain 'SGVsbG8=' 0 0 false"
        ```
*   **Expected Result**:
    -   Alert triggers successfully even if the app GUI was never granted root.
    -   Useful for devices where `su` is only available to `adbd`.

### Case 4: Advanced Parameter Validation
*   **Goal**: Verify that low-level parameters are correctly passed.
*   **Steps**:
    1.  Expand **"Advanced Options"**.
    2.  Change **Serial Number** to a new value (e.g., 5678).
    3.  Change **SIM Slot Index** to `1` (if using a dual-SIM phone with SIM2).
    4.  Trigger the alert.
*   **Expected Result**:
    -   The alert is received on the specified SIM slot.
    -   Changing the serial number allows the system to show the alert again even if a similar one was just dismissed.

### Case 5: Error Handling (No Root)
*   **Goal**: Verify the app handles missing permissions gracefully.
*   **Steps**:
    1.  Use a non-rooted device or deny root permission in Magisk.
    2.  Click **"TRIGGER ALERT (ROOT)"**.
*   **Expected Result**:
    -   A dialog titled "Root Access Required" appears.
    -   No crash occurs.

---

## 3. Troubleshooting

-   **Alert not showing?**
    -   Check if "Wireless Emergency Alerts" are enabled in System Settings.
    -   Check Xposed logs in LSPosed Manager for any "Hook failed" messages.
    -   Ensure the `CLASSPATH` in the ADB command is correct (use `pm path` to verify).
-   **No Sound?**
    -   Check if the phone is in "Do Not Disturb" mode (though Presidential alerts usually bypass this).
    -   Verify the "Alert Sound" setting in WEA system settings.
