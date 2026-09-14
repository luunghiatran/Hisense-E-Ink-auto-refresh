package com.liziwa.hisense_autorefresh.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.AppPreferences
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.Utils

/**
 * Dedicated service for repeatedly e-ink refresh.
 * Uses WakeLock to ensure the timer stays active.
 */
class EInkRepeatedlyRefreshService : Service() {

    private lateinit var prefs: AppPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val notificationUtils by lazy { NotificationUtils.getInstance(this) }

    private var periodRefresh = 0
    private var secondsRemaining = 0
    private var serviceTitle = ""
    private var isScreenOn = true

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        const val NOTIFICATION_ID = 1003
        var isRunning = false
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (secondsRemaining > 0 && isScreenOn) {
                secondsRemaining--
                updateNotification()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private fun updateNotification() {
        val message = if (!isScreenOn) {
            getString(R.string.tv_status_stop) + " || (Screen Off)"
        } else if (secondsRemaining > 0) {
            getString(R.string.notification_repeatedly_countdown, secondsRemaining)
        } else {
            getString(R.string.notification_repeatedly_refreshing)
        }
        
        val notification = notificationUtils.buildNotification(serviceTitle, message)
        notificationUtils.getManager().notify(NOTIFICATION_ID, notification)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (periodRefresh <= 0 || !isScreenOn) return
            XLog.d("RepeatedlyRefresh: Triggered")
            Utils.refreshScreen(applicationContext)
            scheduleNextRefresh()
        }
    }

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CONFIG_CHANGE -> updateConfig()
                Intent.ACTION_SCREEN_OFF -> {
                    XLog.d("RepeatedlyRefresh: Screen OFF, pausing timer")
                    isScreenOn = false
                    stopTimer()
                    updateNotification()
                    releaseWakeLock()
                }
                Intent.ACTION_SCREEN_ON -> {
                    XLog.d("RepeatedlyRefresh: Screen ON, resuming timer")
                    isScreenOn = true
                    acquireWakeLock()
                    updateConfig() // This will restart the timer
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = AppPreferences.getInstance(applicationContext)
        serviceTitle = getString(R.string.btn_monitor_repeatedly)

        val filter = IntentFilter().apply {
            addAction(ACTION_CONFIG_CHANGE)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, eventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, notificationUtils.buildNotification(serviceTitle, getString(R.string.notification_text)))
        updateConfig()
    }

    private fun updateConfig() {
        periodRefresh = prefs.periodRefresh
        if (isScreenOn) {
            scheduleNextRefresh()
        }
    }

    private fun stopTimer() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(countdownRunnable)
    }

    private fun scheduleNextRefresh() {
        stopTimer()

        if (periodRefresh <= 0) {
            secondsRemaining = 0
            updateNotification()
            return
        }

        secondsRemaining = periodRefresh
        updateNotification()
        
        handler.postDelayed(refreshRunnable, periodRefresh * 1000L)
        handler.postDelayed(countdownRunnable, 1000L)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HisenseRefresh:RepeatedlyWakeLock")
            wakeLock?.acquire()
            XLog.d("RepeatedlyRefresh: WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                XLog.d("RepeatedlyRefresh: WakeLock released")
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
        isRunning = false
        stopTimer()
        releaseWakeLock()
        runCatching { unregisterReceiver(eventReceiver) }
        super.onDestroy()
    }
}
