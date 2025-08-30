package com.liziwa.hisense_autorefresh

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppPreferences private constructor(context: Context){

    companion object : SingletonHolder<AppPreferences, Context>(::AppPreferences)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ink_refresh_prefs", Context.MODE_PRIVATE)

    var targetPackageName: String?
        get() = prefs.getString("target_package_name", "")
        set(value) = prefs.edit { putString("target_package_name", value) }


    var serviceSwitch: Boolean
        get() = prefs.getBoolean("service_switch", false)
        set(value) = prefs.edit { putBoolean("service_switch", value).apply() }

    var interval: Int
        get() = prefs.getInt("interval", 10)
        set(value) = prefs.edit { putInt("interval", value).apply() }

    var delayTime: Int
        get() = prefs.getInt("delay_time", 500)
        set(value) = prefs.edit { putInt("delay_time", value).apply() }

    var ignoreTime: Int
        get() = prefs.getInt("ignore_time", 2000)
        set(value) = prefs.edit { putInt("ignore_time", value).apply() }

    var monitorTouch: Boolean
        get() = prefs.getBoolean("monitor_touch", true)
        set(value) = prefs.edit { putBoolean("monitor_touch", value).apply() }

    var monitorKey: Boolean
        get() = prefs.getBoolean("monitor_key", true)
        set(value) = prefs.edit { putBoolean("monitor_key", value).apply() }

    var monitorGlobal: Boolean
        get() = prefs.getBoolean("monitor_global", true)
        set(value) = prefs.edit { putBoolean("monitor_global", value).apply() }

    var permissionIgnoringBatteryOptimizations: Int
        get() = prefs.getInt("permission_ignoring_battery_optimizations", -1)
        set(value) = prefs.edit { putInt("permission_ignoring_battery_optimizations", value).apply() }

    var permissionOverlay: Int
        get() = prefs.getInt("permission_overlay", -1)
        set(value) = prefs.edit { putInt("permission_overlay", value).apply() }

    var permissionUsageStats: Int
        get() = prefs.getInt("permission_usage_stats", -1)
        set(value) = prefs.edit { putInt("permission_usage_stats", value).apply() }
}