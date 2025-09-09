package com.liziwa.hisense_autorefresh

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            XLog.d("BootReceiver: onReceive")
            // 如果服务之前是启动状态，开机后判断服务状态
            if (AppPreferences.getInstance(context).serviceState) {
                scope.launch {
                    delay(5000)
                    checkAccessibilityService(context)
                }
            }
        }
    }

    private fun checkAccessibilityService(context: Context) {
        XLog.d("BootReceiver: checkAccessibilityService")
        // 检查无障碍服务是否已启用
        if (!Utils.isAccessibilityServiceEnabled(context)) {
            showEnableServiceNotification(context)
        } else {
            // 如果已启用，直接启动服务
            val serviceIntent = Intent(context, EInkAccessibilityService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }

    private fun showEnableServiceNotification(context: Context) {
        NotificationUtils.getInstance(context).createErrorNotificationChannel()
        NotificationUtils.getInstance(context).showServiceFailedNotification()
    }
}