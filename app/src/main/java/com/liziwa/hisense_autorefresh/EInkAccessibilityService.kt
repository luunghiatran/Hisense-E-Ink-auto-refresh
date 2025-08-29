package com.liziwa.hisense_autorefresh

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.log

class EInkAccessibilityService : AccessibilityService() {
    private val TAG = "EInkAccessibilityService"

    private lateinit var prefs: AppPreferences

    private var clickCount = 0
    private var lastClickTime: Long = 0
    private var interval = 10
    private var ignoreTime = 0
    private var monitorTouch = true
    private var monitorKey = true
    private var monitorGlobal = true
    private var currentPackage: String? = null
    private var isServiceActive = false
    private var choiceApps: List<String> = mutableListOf()

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        var SERVICE_CONNECT = false
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "accessibility_service_channel"
    }

    fun updateConfig() {
        isServiceActive = prefs.serviceSwitch
        interval = prefs.interval
        ignoreTime = prefs.ignoreTime
        monitorTouch = prefs.monitorTouch
        monitorKey = prefs.monitorKey
        monitorGlobal = prefs.monitorGlobal
        choiceApps = prefs.targetPackageName?.split(",")?.filter({ it.isNotEmpty() }) ?: emptyList()
        clickCount = 0
        Log.d(TAG, "updateConfig: " +
                "isServiceActive=$isServiceActive, " +
                "interval=$interval, " +
                "ignoreTime=$ignoreTime, " +
                "monitorTouch=$monitorTouch, " +
                "monitorKey=$monitorKey, " +
                "monitorGlobal=$monitorGlobal, " +
                "choiceApps=${prefs.targetPackageName}, ")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "无障碍服务创建")
        val filter = IntentFilter(ACTION_CONFIG_CHANGE)
        prefs = AppPreferences.getInstance(applicationContext)
        ContextCompat.registerReceiver(
            this,
            myReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        updateConfig()
    }

    val myReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CONFIG_CHANGE) {
                updateConfig()
            }
        }
    }


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
        SERVICE_CONNECT = true
        // 配置服务
        val info = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        this.serviceInfo = info
        createNotificationChannel()
        startForegroundNotification()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!isServiceActive) return false
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "onKeyEvent: ${event.keyCode}")
            if (monitorKey && (monitorGlobal || (currentPackage != null && choiceApps.contains(currentPackage)))) {
                userOperating()
            }

        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isServiceActive) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }

                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    handleViewClicked(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理无障碍事件时出错", e)
        } finally {
            // 确保资源被正确释放
            try {
                event.source?.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "回收事件资源时出错", e)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val newPackage = event.packageName?.toString()

        if (newPackage != currentPackage) {
            Log.d(TAG, "应用切换: $currentPackage -> $newPackage")
            currentPackage = newPackage
            // 应用切换，重置计数
            if (currentPackage != null && !monitorGlobal && !choiceApps.contains(currentPackage)) {
                //切换到目标之外，重置计数
                clickCount = 0
            }
        }
    }

    private fun handleViewClicked(event: AccessibilityEvent) {
        // 只处理目标应用内的点击
        if (monitorTouch && (monitorGlobal || (currentPackage != null && choiceApps.contains(currentPackage)))) {
            userOperating()
        }
    }

    fun userOperating() {
        val currentTime = SystemClock.elapsedRealtime()
        val timeDiff = currentTime - lastClickTime

        // 过滤5秒内的连续点击
        if (timeDiff > ignoreTime) {
            clickCount++

            Log.d(TAG, "操作计数: $clickCount/$interval, 包名: $currentPackage")

            if (clickCount >= interval) {
                // 触发全局刷新
                performGlobalRefresh()
                clickCount = 0 // 重置计数
                Log.d(TAG, "触发全局刷新")
            }
        }
        lastClickTime = currentTime
    }

    private fun performGlobalRefresh() {
        // 这里调用你的全局刷新代码
        // 例如: yourRefreshFunction()

        // 作为示例，这里发送广播通知可能需要刷新的应用
        try {
            val intent = Intent("com.liziwa.hisense_autorefresh.ACTION_REFRESH_SCREEN")
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "发送刷新广播时出错", e)
        }
    }

    private fun createNotificationChannel() {
        // Android 8.0+ 需要通知渠道
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name), // 用户可见的渠道名称
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_desc)
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun startForegroundNotification() {
        // 创建点击通知时打开的 Intent（通常是应用主界面）
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 构建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setContentTitle(getString(R.string.notification_title)) // 通知标题
            setContentText(getString(R.string.notification_text)) // 通知内容
            setSmallIcon(android.R.drawable.ic_dialog_info) // 必须设置小图标
            setContentIntent(pendingIntent) // 设置点击行为
            priority = NotificationCompat.PRIORITY_LOW
            setCategory(Notification.CATEGORY_SERVICE)
            setOngoing(true) // 设置为持续通知（不可滑动消除）
            setAutoCancel(false) // 禁止自动取消
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
        }.build()

        // 将服务设置为前台服务并显示通知
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "无障碍服务断开连接")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        SERVICE_CONNECT = false
        Log.d(TAG, "无障碍服务被销毁")
        unregisterReceiver(myReceiver)
    }
}