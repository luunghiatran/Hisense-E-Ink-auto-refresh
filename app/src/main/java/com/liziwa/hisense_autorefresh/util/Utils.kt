package com.liziwa.hisense_autorefresh.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.ACCESSIBILITY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.service.EInkAccessibilityService
import java.lang.reflect.InvocationTargetException

/**
 * 通用工具类：无障碍服务状态检测、墨水屏强制刷新、已安装应用枚举。
 */
object Utils {
    /** 判断本应用的无障碍服务是否已在系统设置中开启 */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expectedId = ComponentName(context, EInkAccessibilityService::class.java).flattenToShortString()
        val enabledServices = am.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)
        XLog.d("isAccessibilityServiceEnabled: expectedId=$expectedId, enabledServices=$enabledServices")
        return enabledServices.any { it.id == expectedId }
    }

    /**
     * 强制全屏刷新墨水屏。海信设备通过反射调用厂商 EPD 接口 com.hmct.epd.EpdManager.forceClear()。
     * 不同异常代表不同失败原因：找不到类/方法（error1）、方法调用抛异常（error2）。
     */
    fun refreshScreen(context: Context) {
        XLog.i("refreshScreen: 请求强制刷新墨水屏")
        try {
            Class.forName("com.hmct.epd.EpdManager")
                .getMethod("forceClear")
                .invoke(context.getSystemService("epd"))
            XLog.i("refreshScreen: 刷新调用成功")
        } catch (ex: ReflectiveOperationException) {
            // 未找到 EPD 类或方法，可能非海信设备/系统版本不同
            XLog.e("refreshScreen: 未找到 EPD 接口（error1）", ex)
        } catch (ex: InvocationTargetException) {
            // 找到了方法但执行抛异常
            XLog.e("refreshScreen: 刷新执行异常（error2）", ex)
        }
    }

    /** 判断指定服务是否正在运行 */
    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * 获取所有有界面的应用（排除纯服务应用）。
     * 处理 Android 11+ 包可见性限制：优先 MATCH_ALL，异常时回退到 <queries> 声明的范围。
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
            context.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // 低版本不需要此权限
        }
    }

    /**
     * 判断指定包名是否为系统应用（预装）。
     * 通过 ApplicationInfo.FLAG_SYSTEM 标志识别，系统应用默认不参与读屏判定。
     */
    fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            // 查不到包信息（如已卸载），保守视为非系统应用
            XLog.w("isSystemApp: 未找到包名 $packageName，按非系统处理", e)
            false
        }
    }

    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable
    )
}