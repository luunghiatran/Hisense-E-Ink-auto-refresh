package com.liziwa.hisense_autorefresh

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.Utils

class EInkAccessibilityService : AccessibilityService(), View.OnTouchListener {

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
    private var isTarget = false
    private var serviceSwitch = false
    private var choiceApps: List<String> = mutableListOf()

    private var ignoreApps = arrayOf<String>("com.android.systemui")

    private var addTouchView = false;
    private var serviceConn = false;
    private lateinit var touchView: View

    private val notificationUtils = NotificationUtils.getInstance(this)

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"

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
        isTarget = monitorGlobal || choiceApps.isEmpty()
        XLog.d(
            "updateConfig: " +
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
        if (serviceConn) {
            notificationUtils.showNotification(
                if (serviceSwitch) getString(R.string.notification_text) else getString(R.string.notification_text_stop),
                true
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        XLog.d("无障碍服务创建")
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
        XLog.d("createTouchCapture: ")
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
        XLog.d("deleteTouchCapture: ")
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.removeView(touchView)
        addTouchView = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        XLog.d("无障碍服务已连接")
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
        startForegroundNotification()
        serviceConn = true
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!serviceSwitch) return false
        if (event == null) return false
        if (
            event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_HOME ||
            event.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            event.keyCode == KeyEvent.KEYCODE_MENU
        ) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            XLog.d("onKeyEvent: monitorKey=$monitorKey, isTarget=$isTarget, keyCode=${event.keyCode}")
            if (monitorKey && isTarget) {
                userOperating()
            }

        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            XLog.d("onTouchEvent: monitorTouch=$monitorTouch, isTarget=$isTarget")
            // 只处理目标应用内的点击
            if (monitorTouch && isTarget) {
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
            XLog.e("处理无障碍事件时出错", e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val newPackage = event.packageName?.toString()
        if (newPackage == null) return
        if (ignoreApps.contains(newPackage)) return
        if (newPackage != currentPackage) {
            XLog.d("应用切换: $currentPackage -> $newPackage")
            currentPackage = newPackage

            // 应用切换，重置计数
            if (monitorGlobal || choiceApps.isEmpty() || choiceApps.contains(currentPackage)) {
                isTarget = true
            } else {
                //切换到目标之外，重置计数
                clickCount = 0
                notificationUtils.showNotification(getString(R.string.notification_text), true)
            }
        }
    }

    fun userOperating() {
        if (packageName.equals(currentPackage)) return
        XLog.d("userOperating: ")
        val currentTime = SystemClock.elapsedRealtime()
        val timeDiff = currentTime - lastClickTime

        // 过滤连续点击
        if (timeDiff > ignoreTime) {
            clickCount++

            XLog.d("操作计数: $clickCount/$interval, 包名: $currentPackage")

            if (clickCount >= interval) {
                // 触发全局刷新
                myHandler.removeMessages(MSG_REFRESH_DISPLAY)
                myHandler.sendEmptyMessageDelayed(MSG_REFRESH_DISPLAY, delayTime.toLong())
                clickCount = 0 // 重置计数
                XLog.d("触发全局刷新")
            }

            notificationUtils.showNotification(
                getString(
                    R.string.notification_text_detailed,
                    interval - clickCount
                ), true
            )
        }
        lastClickTime = currentTime
    }

    private fun startForegroundNotification() {
        // 创建点击通知时打开的 Intent（通常是应用主界面）
        notificationUtils.createNotificationChannel()
        val notification = notificationUtils.showNotification(
            if (serviceSwitch) getString(R.string.notification_text) else getString(R.string.notification_text_stop),
            false
        )
        // 将服务设置为前台服务并显示通知
        startForeground(notificationUtils.NOTIFICATION_ID, notification)
    }

    override fun onInterrupt() {
        XLog.d("无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        XLog.d("无障碍服务断开连接")
        serviceConn = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        XLog.d("无障碍服务被销毁")
        deleteTouchCapture()
        unregisterReceiver(myReceiver)
    }
}