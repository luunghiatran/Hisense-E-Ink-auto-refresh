package com.liziwa.hisense_autorefresh.util

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Context.APP_OPS_SERVICE
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.R

/**
 * 运行时权限辅助类：悬浮窗、忽略电池优化、使用情况统计三类权限的检测与申请引导。
 */
object PermissionHelper {

    /** 检查悬浮窗（SYSTEM_ALERT_WINDOW）权限是否已授予 */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** 请求悬浮窗权限：弹引导对话框，按系统版本跳转设置（Android 11+ 带返回标志） */
    fun requestOverlayPermission(activity: Activity, requestCode: Int): AlertDialog {
        XLog.d("requestOverlayPermission: ")
        // 跳转前提示用户
        return AlertDialog.Builder(activity)
            .setTitle(R.string.request_permission_overlay_title)
            .setMessage(R.string.request_permission_overlay_message)
            .setPositiveButton(R.string.btn_to_settings) { dialog, which ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    startOverlaySettings(activity, requestCode)
                } else {
                    requestOverlayPermissionForAndroid11Plus(activity, requestCode)
                }
            }
            .setNegativeButton(R.string.btn_cancel) { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun startOverlaySettings(activity: Activity, requestCode: Int) {
        XLog.d( "startOverlaySettings: ")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${activity.packageName}".toUri()
        )
        activity.startActivityForResult(intent, requestCode)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestOverlayPermissionForAndroid11Plus(activity: Activity, requestCode: Int) {
        XLog.d( "requestOverlayPermissionForAndroid11Plus: ")
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = "package:${activity.packageName}".toUri()
            // 添加额外标志确保返回当前应用
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        activity.startActivityForResult(intent, requestCode)
    }

    /** 跳转系统设置申请忽略电池优化权限 */
    fun requestIgnoreBatteryOptimizationsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:${context.packageName}".toUri()
        context.startActivity(intent)
    }

    fun hasIgnoringBatteryOptimizationsPermission(context: Context): Boolean {
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        val enable = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        XLog.d( "hasIgnoringBatteryOptimizationsPermission: $enable")
        return enable
    }

    /** 检查通知权限（Android 13+） */
    fun hasNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** 打开通知权限设置页 */
    fun requestNotificationsPermission(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
        context.startActivity(intent)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        // 检查使用情况统计权限
        val appOps = context.getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.packageName
        )
        val enable = mode == AppOpsManager.MODE_ALLOWED
        XLog.d( "hasUsageStatsPermission: $enable")
        return enable
    }

    fun requestUsageStatsPermission(context: Context): AlertDialog {
        XLog.d( "requestUsageStatsPermission: ")
        // 跳转前提示用户
        return AlertDialog.Builder(context)
            .setTitle(R.string.request_permission_usage_title)
            .setMessage(R.string.request_permission_usage_message)
            .setPositiveButton(R.string.btn_to_settings) { dialog, which ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.btn_cancel) { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}