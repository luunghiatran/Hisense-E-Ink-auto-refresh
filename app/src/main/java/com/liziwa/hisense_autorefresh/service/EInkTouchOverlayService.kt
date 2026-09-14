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
import android.os.PowerManager
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
 * Uses WakeLock to ensure reliability while the screen is on.
 */
class EInkTouchOverlayService : Service(), View.OnTouchListener {

    private lateinit var prefs: AppPreferences
    private var touchServiceSwitch = false
    private var interval = 10
    private var delayTime = 0
    private var ignoreTime = 2000
    private var serviceTitle = ""
    private var isScreenOn = true

    private var clickCount = 0
    private var lastClickTime: Long = 0

    private var addTouchView = false
    private lateinit var touchView: View

    private val handler = Handler(Looper.getMainLooper())
    private val notificationUtils by lazy { NotificationUtils.getInstance(this) }
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        const val NOTIFICATION_ID = 1004
        var isRunning = false
    }

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CONFIG_CHANGE -> updateConfig()
                Intent.ACTION_SCREEN_OFF -> {
                    XLog.d("TouchOverlay: Screen OFF, disabling overlay")
                    isScreenOn = false
                    deleteTouchCapture()
                    releaseWakeLock()
                    updateNotification(getString(R.string.notification_text_stop) + " (Screen Off)")
                }
                Intent.ACTION_SCREEN_ON -> {
                    XLog.d("TouchOverlay: Screen ON, enabling overlay")
                    isScreenOn = true
                    acquireWakeLock()
                    updateConfig()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        XLog.d("TouchOverlay: Service created")
        prefs = AppPreferences.getInstance(applicationContext)
        serviceTitle = getString(R.string.btn_monitor_draw_over)

        val filter = IntentFilter().apply {
            addAction(ACTION_CONFIG_CHANGE)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, eventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        acquireWakeLock()
        val initialNotification = notificationUtils.buildNotification(serviceTitle, getString(R.string.notification_text))
        startForeground(NOTIFICATION_ID, initialNotification)
        
        updateConfig()
    }

    fun updateConfig() {
        if (!isScreenOn) return

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
        } catch (e: Exception) {
            XLog.e("TouchOverlay: Error removing view", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_OUTSIDE) {
            userOperating()
        }
        return false
    }

    private fun userOperating() {
        if (!touchServiceSwitch || !isScreenOn) return
        
        val currentTime = SystemClock.elapsedRealtime()
        val timeDiff = currentTime - lastClickTime
        
        if (timeDiff > ignoreTime) {
            clickCount++
            if (clickCount >= interval) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    Utils.refreshScreen(applicationContext)
                }, delayTime.toLong())
                clickCount = 0
            }
            updateNotification(getString(R.string.notification_text_detailed, interval - clickCount))
        }
        lastClickTime = currentTime
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HisenseRefresh:TouchWakeLock")
            wakeLock?.acquire()
            XLog.d("TouchOverlay: WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                XLog.d("TouchOverlay: WakeLock released")
            }
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateConfig()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        XLog.d("TouchOverlay: onDestroy")
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        deleteTouchCapture()
        releaseWakeLock()
        runCatching { unregisterReceiver(eventReceiver) }
        super.onDestroy()
    }
}
