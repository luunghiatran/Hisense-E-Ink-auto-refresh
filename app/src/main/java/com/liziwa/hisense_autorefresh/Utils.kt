package com.liziwa.hisense_autorefresh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.elvishew.xlog.XLog
import java.lang.reflect.InvocationTargetException
import kotlin.collections.distinctBy


object Utils {

    private const val TAG = "Utils"
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // 检查无障碍服务是否已启用
        val serviceName = "${context.packageName}.EInkAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        XLog.d(TAG, "isAccessibilityServiceEnabled: serviceName=$enabledServices")
        XLog.d(TAG, "isAccessibilityServiceEnabled: enabledServices=$enabledServices")
        return enabledServices?.contains(serviceName) ?: false
    }

    fun refreshScreen(context: Context) {
        try {
            Class.forName("com.hmct.epd.EpdManager")
                .getMethod("forceClear")
                .invoke(context.getSystemService("epd"))
        } catch (ex: ReflectiveOperationException) {
            XLog.d(TAG, "refreshScreen: error1")
            ex.printStackTrace()
        } catch (ex: InvocationTargetException) {
            XLog.d(TAG, "refreshScreen: error2")
            ex.printStackTrace()
        }
    }

    /**
     * 获取所有有界面的应用（排除纯服务应用）
     */
    fun getLauncherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return when {
            // Android 13+ 需要特殊处理包可见性
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                getLauncherAppsApi33(context, mainIntent)
            }

            else -> {
                getLauncherAppsLegacy(pm, mainIntent)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLauncherAppsLegacy(
        pm: PackageManager,
        intent: Intent
    ): List<AppInfo> {
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolveToAppInfo(pm, it) }
            .distinctBy { it.packageName } // 去重
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getLauncherAppsApi33(
        context: Context,
        intent: Intent
    ): List<AppInfo> {
        val pm = context.packageManager
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())

        return try {
            pm.queryIntentActivities(intent, flags)
                .mapNotNull { resolveToAppInfo(pm, it) }
                .distinctBy { it.packageName }
        } catch (e: SecurityException) {
            // 处理包可见性限制
            if (hasQueryAllPackagesPermission(context)) {
                // 如果有权限但仍有异常，回退到旧方法
                getLauncherAppsLegacy(pm, intent)
            } else {
                // 无权限时尝试使用<queries>声明
                getLauncherAppsViaQueries(pm, intent)
            }
        }
    }

    private fun getLauncherAppsViaQueries(
        pm: PackageManager,
        intent: Intent
    ): List<AppInfo> {
        return try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { resolveToAppInfo(pm, it) }
                .distinctBy { it.packageName }
        } catch (e: Exception) {
            emptyList() // 安全回退
        }
    }

    private fun resolveToAppInfo(
        pm: PackageManager,
        resolveInfo: ResolveInfo
    ): AppInfo? {
        return try {
            val appInfo = resolveInfo.activityInfo.applicationInfo
            AppInfo(
                packageName = appInfo.packageName,
                name = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm)
            )
        } catch (e: Exception) {
            null // 忽略无效条目
        }
    }

    private fun hasQueryAllPackagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // 低版本不需要此权限
        }
    }

    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: android.graphics.drawable.Drawable
    )
}