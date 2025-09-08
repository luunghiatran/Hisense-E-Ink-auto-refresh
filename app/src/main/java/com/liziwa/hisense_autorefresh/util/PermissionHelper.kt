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

object PermissionHelper {

    // 检查悬浮窗权限是否已授予
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // 请求悬浮窗权限（兼容所有版本）
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