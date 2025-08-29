package com.liziwa.hisense_autorefresh
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.annotation.RequiresApi

object AppListHelper {

    /**
     * 获取所有有界面的应用（排除纯服务应用）
     */
    fun getLauncherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return when {
            // Android 11+ 需要特殊处理包可见性
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