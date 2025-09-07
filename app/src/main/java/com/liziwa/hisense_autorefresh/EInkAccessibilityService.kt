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
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog

class EInkAccessibilityService : AccessibilityService(), View.OnTouchListener {
    private val TAG = "EInkAccessibilityService"

    private lateinit var prefs: AppPreferences

    private var clickCount = 0
    private var lastClickTime: Long = 0
    private var interval = 10
    private var ignoreTime = 0

    private var delayTime = 0
    private var monitorTouch = true
    private var monitorKey = true
    private var monitorGlobal = true
    private var currentPackage: String? = null
    private var serviceSwitch = false
    private var choiceApps: List<String> = mutableListOf()

    private var ignoreApps = arrayOf<String>("com.android.systemui")

    private var addTouchView = false;
    private lateinit var touchView: View

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        var SERVICE_CONNECT = false
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "accessibility_service_channel"

        private const val MSG_REFRESH_DISPLAY = 101
    }

    fun updateConfig() {
        serviceSwitch = prefs.serviceSwitch
        interval = prefs.interval
        ignoreTime = prefs.ignoreTime
        delayTime = prefs.delayTime
        monitorTouch = prefs.monitorTouch
        monitorKey = prefs.monitorKey
        monitorGlobal = prefs.monitorGlobal
        choiceApps = prefs.targetPackageName?.split(",")?.filter({ it.isNotEmpty() }) ?: emptyList()
        clickCount = 0
        XLog.d(
            TAG, "updateConfig: " +
                    "serviceSwitch=$serviceSwitch, " +
                    "interval=$interval, " +
                    "ignoreTime=$ignoreTime, " +
                    "monitorTouch=$monitorTouch, " +
                    "monitorKey=$monitorKey, " +
                    "monitorGlobal=$monitorGlobal, " +
                    "choiceApps=${prefs.targetPackageName}, "
        )
        if (prefs.permissionOverlay == 1 && serviceSwitch) {
            createTouchCapture()
        } else {
            deleteTouchCapture()
        }
    }

    override fun onCreate() {
        super.onCreate()
        XLog.d(TAG, "无障碍服务创建")
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

    var myHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == MSG_REFRESH_DISPLAY) {
                Utils.refreshScreen(applicationContext)
            }
        }
    }

    private fun createTouchCapture() {
        if (addTouchView) return
        XLog.d(TAG, "createTouchCapture: ")
        if (!this::touchView.isInitialized) {
            touchView = View(applicationContext)
            touchView.setOnTouchListener(this)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        val lp = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSPARENT
        )

        // 添加到窗口管理器
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(touchView, lp)
        addTouchView = true
    }

    private fun deleteTouchCapture() {
        if (!addTouchView) return
        XLog.d(TAG, "deleteTouchCapture: ")
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.removeView(touchView)
        addTouchView = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        XLog.d(TAG, "无障碍服务已连接")
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
        if (!serviceSwitch) return false
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            XLog.d(
                TAG, "onKeyEvent: monitorKey=$monitorKey, " +
                        "monitorGlobal=$monitorGlobal, " +
                        "currentPackage=$currentPackage, " +
                        "choiceApps=${choiceApps.size}"
            )
            if (monitorKey && (monitorGlobal || choiceApps.isEmpty() || (currentPackage != null && choiceApps.contains(
                    currentPackage
                )))
            ) {
                userOperating()
            }

        }
        return false
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            XLog.d(
                TAG, "onTouchEvent: monitorTouch=$monitorTouch, " +
                        "monitorGlobal=$monitorGlobal, " +
                        "currentPackage=$currentPackage, " +
                        "choiceApps=${choiceApps.size}"
            )
            // 只处理目标应用内的点击
            if (monitorTouch && (monitorGlobal || choiceApps.isEmpty() || (currentPackage != null && choiceApps.contains(
                    currentPackage
                )))
            ) {
                userOperating()
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!serviceSwitch) return

        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handleWindowStateChanged(event)
            }
        } catch (e: Exception) {
            XLog.e(TAG, "处理无障碍事件时出错", e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val newPackage = event.packageName?.toString()
        if (ignoreApps.contains(newPackage)) return
        if (newPackage != currentPackage) {
            XLog.d(TAG, "应用切换: $currentPackage -> $newPackage")
            currentPackage = newPackage
            // 应用切换，重置计数
            if (currentPackage != null && !monitorGlobal && !choiceApps.isEmpty() && !choiceApps.contains(
                    currentPackage
                )
            ) {
                //切换到目标之外，重置计数
                clickCount = 0
            }
        }
    }

    fun userOperating() {
        if (packageName.equals(currentPackage)) return
        XLog.d(TAG, "userOperating: ")
        val currentTime = SystemClock.elapsedRealtime()
        val timeDiff = currentTime - lastClickTime

        // 过滤5秒内的连续点击
        if (timeDiff > ignoreTime) {
            clickCount++

            XLog.d(TAG, "操作计数: $clickCount/$interval, 包名: $currentPackage")

            if (clickCount >= interval) {
                // 触发全局刷新
                myHandler.removeMessages(MSG_REFRESH_DISPLAY)
                myHandler.sendEmptyMessageDelayed(MSG_REFRESH_DISPLAY, delayTime.toLong())
                clickCount = 0 // 重置计数
                XLog.d(TAG, "触发全局刷新")
            }
        }
        lastClickTime = currentTime
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
            setSmallIcon(R.drawable.icon) // 必须设置小图标
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
        XLog.d(TAG, "无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        XLog.d(TAG, "无障碍服务断开连接")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        SERVICE_CONNECT = false
        XLog.d(TAG, "无障碍服务被销毁")
        deleteTouchCapture()
        unregisterReceiver(myReceiver)
    }
}