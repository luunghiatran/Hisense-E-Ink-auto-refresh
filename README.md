# Hisense E-ink Auto Refresh（Hisense E-Ink Auto Refresh）

A **forced full-screen refresh** tool for Hisense E-Ink devices. It monitors touch / key operation counts in foreground applications and invokes the vendor EPD interface to perform a global refresh when the configured threshold is reached, effectively eliminating E-Ink ghosting during reading.

> Referenced from the RefreshAi project; since the original project had no Chinese support and limited functionality, it was implemented independently and supplemented with automatic reading screen detection, repeatedly refresh, debug mode, and other capabilities.

---

## Features

- **Operation Count Refresh**: Counts touch / key operations within the specified scope and automatically refreshes once the accumulated count reaches the "Refresh Interval".
- **Configurable Monitoring Scope**:
  - Monitor all applications (default)
  - Monitor only specified applications ("Configure Monitored Apps")
- **Automatic Reading Screen Detection**: When enabled, screen analysis is performed 1 second after a screen switch. A reading screen is characterized by "**Body text > 150 characters, and Character Count / Node Count > 10**" (text-dense, node-sparse), preventing accidental refreshes on non-reading screens such as menus and dense lists.
- **Ignored Applications (Whitelist)**: Some paid reading applications cannot be analyzed after encryption. They can be added to the whitelist and will be **directly treated as reading screens and skip screen analysis**, with the highest priority (effective even if a system application matches the whitelist).
- **Automatic Skip for System Applications**: If the switched screen belongs to a system application, it is not treated as a reading screen by default and screen analysis is skipped.
- **Fallback Re-detection for Non-reading Screens**: Accessibility events may not be triggered when switching between multiple fragments within the same activity, so screen analysis is automatically performed again after **5 operations** on a non-reading screen to prevent missed detection.
- **Repeatedly Refresh**: Forces a refresh at a fixed time interval (seconds), independent of operation counting. Default is 300 seconds (5 minutes), set 0 to disable. Timing only runs when the screen is unlocked; pauses when locked and resets after unlocking. If less than 30 seconds have passed since the last refresh, the refresh is skipped to avoid duplication with operation-triggered refreshes. Operation count is reset after a successful refresh.
- **Debug Mode**: Enabled by clicking the main screen title **10 times** consecutively (disabled by default). When enabled, XLog outputs thread information / call stacks / borders and writes logs to files (`xlog/` directory, archived daily, retained for 7 days). When disabled, logs are only output to Logcat and no files are written.
- **Auto Start on Boot**: Automatically starts the accessibility service and monitoring after device reboot.
- **Application-specific Refresh Parameters**: In the "Monitored Applications" list, each application can independently configure "Refresh Trigger Count" and "Trigger Delay", overriding the global default values (default 10 times / 500ms). The configuration button behind the application name is **disabled by default and can only be clicked after the application is selected**.

---

## How It Works

- Uses **AccessibilityService** (`TYPE_WINDOW_STATE_CHANGED` + `TYPE_VIEW_CLICKED`) to monitor foreground screen switches and user operations.
- When the screen changes, whether it is a reading screen is determined according to the priority of "Whitelist → System Application → Screen Analysis".
- Screen analysis traverses the current window nodes and **only counts text content whose node itself and all ancestor nodes are visible** (excluding hidden nodes), and determines whether it is a reading screen based on text volume and node density.
- Repeatedly refresh is driven by a `Handler` loop timer and is independent of operation counting.
- Refresh is implemented through reflection by calling Hisense `com.hmct.epd.EpdManager.forceClear()`.
- Configuration changes are **hot-updated** through the `ACTION_CONFIG_CHANGE` broadcast without restarting the service.

---

## Permissions and Dependencies

| Permission / Component | Purpose |
| --- | --- |
| Accessibility Service (AccessibilityService) | Monitor screen switches and operation events (core) |
| `SYSTEM_ALERT_WINDOW` | Overlay monitoring of touch events (optional, required when touch monitoring is enabled) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent background process termination by battery optimization policies |
| `RECEIVE_BOOT_COMPLETED` | Auto start on boot |
| `QUERY_ALL_PACKAGES` | Enumerate installed applications (Android 11+ package visibility) |
| `POST_NOTIFICATIONS` | Foreground service notifications |

> Note: Forced refresh depends on the Hisense private EPD interface `com.hmct.epd.EpdManager`. It may not be available on non-Hisense devices or different system versions (error logs will be recorded and other functions will not be affected).

---

## Usage

1. **Install ADB Driver**: Install the adb tool on your computer (please search and download it yourself).
2. **Enable USB Debugging**: Open "Developer Options → USB Debugging" on the device, connect it to your computer, and select **Allow** in the popup.
3. **Remove Hidden API Restrictions** (Important):
   ```bash
   adb shell settings put global hidden_api_policy 0
   adb shell settings get global hidden_api_policy   # Output 0 indicates success
   ```

4. **Install and Grant Permissions**: After rebooting the device, install the APK and grant the required permissions such as Accessibility Service and Overlay Window.

5. **Test Refresh**: Click **Test Refresh** in the application. If the E-Ink screen performs a global refresh successfully, the interface is accessible and monitoring parameters can be configured freely.

---

## Configuration

| Option | Description |
| --- | --- |
| Monitor All Applications | Count operations for all foreground applications when enabled; when disabled, targets must be specified through "Configure Monitored Apps" |
| Respond to Touch / Key | Independently controls whether touch and key operations are counted |
| Refresh Interval | Number of accumulated operations required to trigger a refresh |
| Ignore Time | Minimum interval between two operations (milliseconds), used to filter consecutive duplicate triggers |
| Automatically Detect Reading Screens | Determines reading screens based on text volume and node density when enabled (see above) |
| Repeatedly Refresh (seconds) | Force refresh at a fixed interval, 0 to disable, default 300 (5 minutes) |
| Configure Ignored Applications | Open the "Ignored Applications" list and add encrypted / special applications to the whitelist |
| Configure Monitored Applications | Open the "Monitored Applications" list and select applications to monitor. After selection, the "Configure" button next to the application name is enabled. Clicking it allows independent configuration of refresh count and delay for that application (default 10 times / 2000ms), and the current configuration is displayed in real time |
| Debug Mode | Enabled by clicking the main title 10 times; logs are written to files and detailed information is output |

---

## Debug Mode (Troubleshooting)

- **Enable**: Click the top title text 10 times consecutively on the main screen. A "Debug Mode Enabled" message will appear.
- **Effect**: XLog enables thread information, call stacks, and borders simultaneously, and writes logs to `Android/data/<package name>/files/xlog/` (archived daily and retained for 7 days).
- **Disable**: Click the title 10 times again. Logs stop being written to files and revert to the simplified format.
- Debug logs are very helpful for locating issues such as "Why didn't it refresh?", "Why was it recognized as a reading screen?", and "Repeatedly refresh timing".

---

## Notes

- Automatic detection depends on screen analysis and is only effective for **visible text**. Encrypted content, WebView, canvas-rendered content, and other non-standard text interfaces may not be detected correctly and should be added to the whitelist.
- System applications and this application's own screens do not participate in screen analysis by default.
- E-Ink screen refresh is relatively slow. Setting the refresh interval too low may cause noticeable flashing. Please adjust according to your reading habits.
- Debug mode writes logs to storage. Keeping it enabled for a long time may consume a small amount of storage space (retention is limited to 7 days).

---

## Screenshots

screenshot/screenshot_1.png
screenshot/screenshot_2.png
screenshot/screenshot_3.png

---

## Project Structure

- `EInkAccessibilityService.kt`: Core accessibility service responsible for screen monitoring, screen analysis, counting, repeatedly refresh, and refresh triggering.
- `AppPreferences.kt`: SharedPreferences configuration wrapper (including repeatedly refresh, debug mode switches, etc.).
- `activity/MainActivity.kt`: Main configuration screen (including the debug mode entry and custom title bar).
- `activity/AppsActivity.kt`: Application selection list (reused for both "Monitored Applications" and "Ignored Applications" modes, including custom title bar, back button, and per-application configuration dialog).
- `view/AppListAdapter.kt`: Application list adapter (DataBinding), including `ListItem` (application name / package name / icon / selection state / independent refresh parameters), responsible for enabling the configuration button according to selection state and displaying configuration information.
- `util/Utils.kt`: Utilities such as refresh interface, system application detection, and application enumeration.
- `util/NotificationUtils.kt` / `BootReceiver.kt` / `MyApp.kt`: Notifications, auto start on boot, and XLog initialization (switching file output according to debug mode).
- `util/DateFileNameGenerator.kt`: XLog date-based archive filename generator.