package com.liziwa.hisense_autorefresh.service

import com.liziwa.hisense_autorefresh.AppPreferences
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.Utils

/**
 * Core accessibility service: monitors foreground screen changes, key interactions, and view clicks.
 */
class EInkAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppPreferences
    private val notificationUtils by lazy { NotificationUtils.getInstance(this) }

    private var clickCount = 0
    private var lastClickTime: Long = 0
    private var interval = 5
    private var ignoreTime = 1000
    private var delayTime = 100
    private var monitorKey = true
    private var monitorGlobal = true
    private var autoDetectReading = true
    private var readingWhitelist: List<String> = emptyList()
    private var currentPackage: String? = null
    private var currentActivity: String? = null
    private var isTarget = false
    private var isReading = false
    private var serviceSwitch = false
    private var choiceApps: List<String> = emptyList()
    private var appConfigs: Map<String, Pair<Int, Int>> = emptyMap()
    private var serviceTitle = ""

    private var isScreenLocked = false
    private var nonReadingOpCount = 0
    private val ignoreApps = arrayOf("com.android.systemui")

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        const val NOTIFICATION_ID = 1002
        var isRunning = false

        private const val MSG_REFRESH_DISPLAY = 101
        private const val MSG_DETECT_READING = 102
        private const val DETECT_DELAY_MS = 1000L
        private const val READ_RATIO_THRESHOLD = 10
        private const val READ_TEXT_THRESHOLD = 150
        private const val NON_READING_REDETECT_COUNT = 5
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CONFIG_CHANGE -> updateConfig()
                Intent.ACTION_SCREEN_OFF -> { isScreenLocked = true; clickCount = 0; setReadingState(false) }
                Intent.ACTION_SCREEN_ON -> { isScreenLocked = false; setReadingState(false) }
            }
        }
    }

    fun updateConfig() {
        serviceSwitch = prefs.serviceSwitch
        interval = prefs.interval
        ignoreTime = prefs.ignoreTime
        delayTime = prefs.delayTime
        monitorKey = prefs.monitorKey
        monitorGlobal = prefs.monitorGlobal
        autoDetectReading = prefs.autoDetectReading
        readingWhitelist = prefs.readingWhitelist?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        choiceApps = prefs.targetPackageName?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        
        appConfigs = prefs.appRefreshConfigs?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { seg ->
            val parts = seg.split(":")
            if (parts.size == 3) {
                val iv = parts[1].toIntOrNull() ?: AppPreferences.DEFAULT_APP_INTERVAL
                val dv = parts[2].toIntOrNull() ?: AppPreferences.DEFAULT_APP_DELAY
                parts[0] to (iv to dv)
            } else null
        }?.toMap() ?: emptyMap()
        
        clickCount = 0
        isTarget = monitorGlobal || choiceApps.isEmpty()
        isReading = !autoDetectReading

        XLog.d("AccService config: switch=$serviceSwitch, isTarget=$isTarget, interval=$interval")
        updateNotification(if (serviceSwitch) getString(R.string.notification_text) else getString(R.string.notification_text_stop))
    }

    private fun updateNotification(text: String) {
        val notification = notificationUtils.buildNotification(serviceTitle, text)
        notificationUtils.getManager().notify(NOTIFICATION_ID, notification)
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = AppPreferences.getInstance(applicationContext)
        serviceTitle = getString(R.string.btn_monitor_interaction)
        
        val filter = IntentFilter(ACTION_CONFIG_CHANGE).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, configReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        startForeground(NOTIFICATION_ID, notificationUtils.buildNotification(serviceTitle, getString(R.string.notification_text)))
        updateConfig()
    }

    private val myHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REFRESH_DISPLAY -> {
                    XLog.i("AccService: EXECUTING REFRESH SCREEN")
                    Utils.refreshScreen(applicationContext)
                }
                MSG_DETECT_READING -> detectReadingScreen()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        XLog.d("AccService: onServiceConnected")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        updateConfig()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!serviceSwitch || event == null || isScreenLocked) return false
        if (event.keyCode in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_MENU)) return false
        
        if (event.action == KeyEvent.ACTION_DOWN && monitorKey && isTarget) {
            XLog.d("AccService: Key event detected: ${event.keyCode}")
            userOperating()
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!serviceSwitch) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (isTarget) {
                    XLog.d("AccService: View clicked in target app")
                    userOperating()
                }
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val newPackage = event.packageName?.toString() ?: return
        if (newPackage in ignoreApps) return

        val newActivity = event.className?.toString()
        if (newPackage == currentPackage && (newActivity == null || newActivity == currentActivity)) return

        XLog.d("AccService: window changed to $newPackage / $newActivity")
        currentPackage = newPackage
        if (newActivity != null) currentActivity = newActivity
        isTarget = monitorGlobal || choiceApps.isEmpty() || choiceApps.contains(currentPackage)
        myHandler.removeMessages(MSG_DETECT_READING)

        if (!serviceSwitch || !isTarget) {
            clickCount = 0
            updateNotification(getString(R.string.notification_text))
            return
        }

        if (autoDetectReading) {
            if (isInReadingWhitelist(newPackage)) setReadingState(true)
            else if (newPackage == applicationContext.packageName || Utils.isSystemApp(applicationContext, newPackage)) setReadingState(false)
            else {
                setReadingState(false)
                myHandler.sendEmptyMessageDelayed(MSG_DETECT_READING, DETECT_DELAY_MS)
            }
        } else setReadingState(true)
    }

    private fun isInReadingWhitelist(pkg: String) = readingWhitelist.any { pkg.contains(it, ignoreCase = true) }

    private fun detectReadingScreen() {
        if (!serviceSwitch || !isTarget) return
        val root = rootInActiveWindow ?: return
        val result = countScreenText(root)
        val reading = result.textCount > READ_TEXT_THRESHOLD && (result.nodeCount > 0 && result.textCount / result.nodeCount > READ_RATIO_THRESHOLD)
        XLog.d("AccService: reading detection result=$reading (text=${result.textCount}, nodes=${result.nodeCount})")
        setReadingState(reading)
        if (!reading) {
            clickCount = 0
            updateNotification(getString(R.string.notification_text))
        }
    }

    private data class ScreenTextResult(val textCount: Int, val nodeCount: Int)

    private fun countScreenText(root: AccessibilityNodeInfo): ScreenTextResult {
        var total = 0
        var nodes = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            nodes++
            if (!node.isVisibleToUser) continue
            val childHasText = (0 until node.childCount).any { i ->
                node.getChild(i)?.let { it.text?.isNotBlank() == true || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && it.stateDescription?.isNotBlank() == true) } ?: false
            }
            if (!childHasText && !node.isEditable) {
                val text = node.text ?: node.contentDescription
                if (text?.isNotBlank() == true && isTrulyVisible(node)) total += text.length
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let { stack.add(it) } }
        }
        return ScreenTextResult(total, nodes)
    }

    private fun isTrulyVisible(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (!current.isVisibleToUser) return false
            current = current.parent
        }
        return true
    }

    private fun setReadingState(reading: Boolean) {
        if (isReading != reading) {
            XLog.d("AccService: reading state changed to $reading")
            isReading = reading
            clickCount = 0
            nonReadingOpCount = 0
        }
    }

    private fun userOperating() {
        if (packageName == currentPackage) return
        
        if (autoDetectReading && !isReading) {
            if (++nonReadingOpCount >= NON_READING_REDETECT_COUNT) {
                nonReadingOpCount = 0
                XLog.d("AccService: non-reading op limit reached, re-detecting screen")
                myHandler.removeMessages(MSG_DETECT_READING)
                myHandler.sendEmptyMessageDelayed(MSG_DETECT_READING, DETECT_DELAY_MS)
            }
            return
        }
        
        val config = if (monitorGlobal) interval to delayTime else appConfigs[currentPackage] ?: (AppPreferences.DEFAULT_APP_INTERVAL to AppPreferences.DEFAULT_APP_DELAY)
        val currentTime = SystemClock.elapsedRealtime()
        val timeDiff = currentTime - lastClickTime
        
        if (timeDiff > ignoreTime) {
            clickCount++
            XLog.d("AccService: VALID OPERATION. clickCount=$clickCount/${config.first}")

            if (clickCount >= config.first) {
                XLog.d("AccService: Threshold met, scheduling refresh in ${config.second}ms")
                myHandler.removeMessages(MSG_REFRESH_DISPLAY)
                myHandler.sendEmptyMessageDelayed(MSG_REFRESH_DISPLAY, config.second.toLong())
                clickCount = 0
            }
            updateNotification(getString(R.string.notification_text_detailed, config.first - clickCount))
        } else {
            XLog.d("AccService: operation ignored (gap too small: ${timeDiff}ms)")
        }
        lastClickTime = currentTime
    }

    override fun onInterrupt() {
        XLog.d("AccService: onInterrupt")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        XLog.d("AccService: onUnbind")
        myHandler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        XLog.d("AccService: onDestroy")
        myHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(configReceiver) }
        super.onDestroy()
    }
}
