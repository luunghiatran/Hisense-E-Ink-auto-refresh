package com.liziwa.hisense_autorefresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.service.EInkAccessibilityService
import com.liziwa.hisense_autorefresh.service.EInkRepeatedlyRefreshService
import com.liziwa.hisense_autorefresh.service.EInkTouchOverlayService
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.PermissionHelper
import com.liziwa.hisense_autorefresh.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开机广播接收器：设备启动完成后，自启已开启的服务。
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            XLog.d("BootReceiver: onReceive 开机完成")
            val prefs = AppPreferences.getInstance(context)
            if (prefs.autoStartOnBoot) {
                scope.launch {
                    delay(5000)
                    checkAndStartServices(context, prefs)
                }
            }
        }
    }

    private fun checkAndStartServices(context: Context, prefs: AppPreferences) {
        // 1. Accessibility Service
        if (prefs.serviceSwitch) {
            if (Utils.isAccessibilityServiceEnabled(context)) {
                context.startForegroundService(Intent(context, EInkAccessibilityService::class.java))
            } else {
                NotificationUtils.getInstance(context).createErrorNotificationChannel()
                NotificationUtils.getInstance(context).showServiceFailedNotification()
            }
        }

        // 2. Touch Overlay Service
        if (prefs.touchServiceSwitch && PermissionHelper.hasOverlayPermission(context)) {
            context.startForegroundService(Intent(context, EInkTouchOverlayService::class.java))
        }

        // 3. Repeatedly Refresh Service
        if (prefs.repeatedlyServiceSwitch) {
            context.startForegroundService(Intent(context, EInkRepeatedlyRefreshService::class.java))
        }
    }
}
