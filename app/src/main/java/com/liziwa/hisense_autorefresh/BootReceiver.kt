package com.liziwa.hisense_autorefresh

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机自启动服务
            startAccessibilityService( context)
        }
    }

    private fun startAccessibilityService(context: Context) {
        // 检查无障碍服务是否已启用
        if (!Utils.isAccessibilityServiceEnabled(context)) {
            // 如果未启用，跳转到无障碍设置页面
            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(accessibilityIntent)

            // 可以添加通知提醒用户启用服务
            showEnableServiceNotification(context)
        } else {
            // 如果已启用，直接启动服务
            val serviceIntent = Intent(context, EInkAccessibilityService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }

    private fun showEnableServiceNotification(context: Context) {
        // 创建通知提醒用户启用无障碍服务
        // 实现通知创建逻辑（参考之前的通知创建代码）
    }
}