# Walkthrough - Fixing "Bad notification for startForeground" Crash

The application was crashing with a `RemoteServiceException: Bad notification for startForeground`. This error occurred because the services were attempting to start in the foreground using a notification channel that had not been created. Since the app targets Android 9.0 (API 28), notification channels are mandatory.

## Changes Made

### 1. Notification Channel Initialization
Modified [NotificationUtils.kt](file:///D:/Projects/Android/Hisense-E-Ink-auto-refresh-main/app/src/main/java/com/liziwa/hisense_autorefresh/util/NotificationUtils.kt) to ensure that notification channels are created as soon as the utility is initialized.

- Added calls to `createNotificationChannel()` and `createErrorNotificationChannel()` in the `init` block.
- Fixed a missing resource reference by changing `R.drawable.icon_small` to `R.drawable.icon` in `showServiceFailedNotification`.

### 2. Manifest Permissions and Service Types
Updated [AndroidManifest.xml](file:///D:/Projects/Android/Hisense-E-Ink-auto-refresh-main/app/src/main/AndroidManifest.xml) to better support newer Android versions (especially Android 14+), even though the current target is API 30.

- Added `FOREGROUND_SERVICE_SPECIAL_USE` permission.
- Added `<property>` tags for `specialUse` service types in `EInkRepeatedlyRefreshService` and `EInkTouchOverlayService` to provide required metadata for these foreground services.

## Verification Results

### Automated Tests
- Not applicable for this UI/Service initialization fix.

### Manual Verification
- The app should now be able to start the Accessibility Service, Repeatedly Refresh Service, and Touch Overlay Service without crashing on devices running Android 8.0 or higher.
- Notification channels "E-Ink Services" and "Service Errors" will be correctly registered in the system settings.
