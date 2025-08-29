package com.liziwa.hisense_autorefresh

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.lang.reflect.InvocationTargetException



class Utils {
    companion object{
        private const val TAG = "Utils"
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            // 检查无障碍服务是否已启用
            val serviceName = "${context.packageName}.EInkAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.d(TAG, "isAccessibilityServiceEnabled: $enabledServices")
            return enabledServices?.contains(serviceName) ?: false
        }

        fun refreshScreen(context: Context) {
            try {
                Class.forName("com.hmct.epd.EpdManager")
                    .getMethod("forceClear")
                    .invoke(context.getSystemService("epd"))
            } catch (ex: ReflectiveOperationException) {
                Log.d(TAG, "refreshScreen: error1")
                ex.printStackTrace()
            } catch (ex: InvocationTargetException) {
                Log.d(TAG, "refreshScreen: error2")
                ex.printStackTrace()
            }
        }

    }
}