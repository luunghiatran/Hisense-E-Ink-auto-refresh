package com.liziwa.hisense_autorefresh.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.AppPreferences
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.PermissionHelper
import com.liziwa.hisense_autorefresh.util.Utils

/**
 * Service dedicated to capturing touch events using a transparent overlay.
 */
class EInkTouchOverlayService : Service(), View.OnTouchListener {

    private lateinit var prefs: AppPreferences
    private var touchServiceSwitch = false
    private var interval = 5
    private var delayTime = 100
    private var ignoreTime = 1000
    private var serviceTitle = ""

    private var clickCount = 0
    private var lastClickTime: Long = 0

    private var addTouchView = false
    private lateinit var touchView: View

    private val handler = Handler(Looper.getMainLooper())
    private val notificationUtils by lazy { NotificationUtils.getInstance(this) }

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        const val NOTIFICATION_ID = 1004
        var isRunning = false
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            XLog.d("TouchOverlay: Received broadcast ${intent?.action}")
            if (intent?.action == ACTION_CONFIG_CHANGE) updateConfig()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        XLog.d("TouchOverlay: Service created")
        prefs = AppPreferences.getInstance(applicationContext)
        serviceTitle = getString(R.string.btn_monitor_draw_over)

        val filter = IntentFilter(ACTION_CONFIG_CHANGE)
        ContextCompat.registerReceiver(this, configReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Important: startForeground must be called within 5 seconds of service start
        val initialNotification = notificationUtils.buildNotification(serviceTitle, getString(R.string.notification_text))
        startForeground(NOTIFICATION_ID, initialNotification)
        
        updateConfig()
    }

    fun updateConfig() {
        touchServiceSwitch = prefs.touchServiceSwitch
        interval = prefs.touchInterval
        delayTime = prefs.touchDelayTime
        ignoreTime = prefs.touchIgnoreTime
        XLog.d("TouchOverlay config: switch=$touchServiceSwitch, interval=$interval, delay=$delayTime, ignore=$ignoreTime")

        if (PermissionHelper.hasOverlayPermission(this) && touchServiceSwitch) {
            createTouchCapture()
        } else {
            deleteTouchCapture()
        }

        if (!touchServiceSwitch) {
            notificationUtils.cancelNotification(NOTIFICATION_ID)
        }

        updateNotification(if (touchServiceSwitch) getString(R.string.notification_text) else getString(R.string.notification_text_stop))
    }

    private fun updateNotification(text: String) {
        XLog.d("TouchOverlay: notify with text: $text")
        val notification = notificationUtils.buildNotification(serviceTitle, text)
        notificationUtils.getManager().notify(NOTIFICATION_ID, notification)
    }

    private fun createTouchCapture() {
        if (addTouchView) return
        XLog.d("TouchOverlay: createTouchCapture")
        if (!this::touchView.isInitialized) {
            touchView = View(applicationContext)
            touchView.setOnTouchListener(this)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR

        // Small 2x2 view, placed in a corner
        val lp = WindowManager.LayoutParams(
            2, 2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            wm.addView(touchView, lp)
            addTouchView = true
            XLog.d("TouchOverlay: View added to WindowManager")
        } catch (e: Exception) {
            XLog.e("TouchOverlay: Error adding view", e)
        }
    }

    private fun deleteTouchCapture() {
        if (!addTouchView) return
        XLog.d("TouchOverlay: deleteTouchCapture")
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(touchView)
            addTouchView = false
            XLog.d("TouchOverlay: View removed from WindowManager")
        } catch (e: Exception) {
            XLog.e("TouchOverlay: Error removing view", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        // Log all outside events for debugging
        if (event?.action == MotionEvent.ACTION_OUTSIDE) {
            XLog.d("TouchOverlay: onTouch ACTION_OUTSIDE")
            userOperating()
        }
        return false
    }

    private fun userOperating() {
        if (!touchServiceSwitch) {
            XLog.d("TouchOverlay: userOperating ignored (switch OFF)")
            return
        }
        
        val currentTime = SystemClock.elapsedRealtime()
        val timeDiff = currentTime - lastClickTime
        
        if (timeDiff > ignoreTime) {
            clickCount++
            XLog.i("TouchOverlay: HIT! count=$clickCount/$interval")

            if (clickCount >= interval) {
                XLog.i("TouchOverlay: Threshold met, refreshing screen in ${delayTime}ms")
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    XLog.i("TouchOverlay: Executing refreshScreen()")
                    Utils.refreshScreen(applicationContext)
                }, delayTime.toLong())
                clickCount = 0
            }
            
            val statusText = getString(R.string.notification_text_detailed, interval - clickCount)
            updateNotification(statusText)
        } else {
            XLog.d("TouchOverlay: Debounced (gap: ${timeDiff}ms)")
        }
        lastClickTime = currentTime
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        XLog.d("TouchOverlay: onStartCommand")
        updateConfig()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        XLog.d("TouchOverlay: onDestroy")
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        deleteTouchCapture()
        runCatching { unregisterReceiver(configReceiver) }
        super.onDestroy()
    }
}
