package com.liziwa.hisense_autorefresh.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.AppPreferences
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.util.NotificationUtils
import com.liziwa.hisense_autorefresh.util.Utils

/**
 * Dedicated service for repeatedly e-ink refresh.
 */
class EInkRepeatedlyRefreshService : Service() {

    private lateinit var prefs: AppPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val notificationUtils by lazy { NotificationUtils.getInstance(this) }

    private var periodRefresh = 0
    private var secondsRemaining = 0
    private var serviceTitle = ""

    companion object {
        const val ACTION_CONFIG_CHANGE = "com.liziwa.hisense_autorefresh.ACTION_CONFIG_CHANGE"
        const val NOTIFICATION_ID = 1003
        var isRunning = false
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (secondsRemaining > 0) {
                secondsRemaining--
                updateNotification()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private fun updateNotification() {
        val message = if (secondsRemaining > 0) {
            getString(R.string.notification_repeatedly_countdown, secondsRemaining)
        } else {
            getString(R.string.notification_repeatedly_refreshing)
        }
        
        val notification = notificationUtils.buildNotification(serviceTitle, message)
        notificationUtils.getManager().notify(NOTIFICATION_ID, notification)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (periodRefresh <= 0) return
            XLog.d("Repeatedly refresh triggered")
            Utils.refreshScreen(applicationContext)
            scheduleNextRefresh()
        }
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CONFIG_CHANGE) updateConfig()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = AppPreferences.getInstance(applicationContext)
        serviceTitle = getString(R.string.btn_monitor_repeatedly)

        ContextCompat.registerReceiver(this, configReceiver, IntentFilter(ACTION_CONFIG_CHANGE), ContextCompat.RECEIVER_NOT_EXPORTED)

        startForeground(NOTIFICATION_ID, notificationUtils.buildNotification(serviceTitle, getString(R.string.notification_text)))
        updateConfig()
    }

    private fun updateConfig() {
        periodRefresh = prefs.periodRefresh
        scheduleNextRefresh()
    }

    private fun scheduleNextRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(countdownRunnable)

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateConfig()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(configReceiver) }
        super.onDestroy()
    }
}
