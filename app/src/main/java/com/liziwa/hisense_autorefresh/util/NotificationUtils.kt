package com.liziwa.hisense_autorefresh.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.activity.MainActivity
import com.liziwa.hisense_autorefresh.service.EInkTouchOverlayService

/**
 * Utility for managing foreground service notifications.
 */
class NotificationUtils private constructor(private val context: Context) {

    private val CHANNEL_ERROR_ID = "service_error_channel"
    private val CHANNEL_ID = "service_channel"

    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val pendingIntent: PendingIntent

    companion object : SingletonHolder<NotificationUtils, Context>(::NotificationUtils)

    init {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Ensure channels are created before they are used by startForeground
        createNotificationChannel()
        createErrorNotificationChannel()
    }

    fun createErrorNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ERROR_ID, "Service Errors", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for service errors"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun createNotificationChannel() {
        // Using IMPORTANCE_DEFAULT to ensure visibility on all E-Ink devices
        val channel = NotificationChannel(CHANNEL_ID, "E-Ink Services", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Foreground notifications for E-Ink services"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun showServiceFailedNotification() {
        XLog.d("NotificationUtils: showServiceFailedNotification")
        val notification = NotificationCompat.Builder(context, CHANNEL_ERROR_ID)
            .setContentTitle(context.getString(R.string.notification_title_error))
            .setContentText(context.getString(R.string.notification_desc_error))
            .setSmallIcon(R.drawable.icon_small)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(1001, notification)
    }

    fun removeErrorNotification() {
        manager.cancel(1001)
    }

    /**
     * Builds a notification for a service.
     * @param title The title of the notification (Service name)
     * @param text The status text (Countdown or actions)
     */
    fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.icon_small)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun getManager(): NotificationManager = manager
    fun cancelNotification(notificationId: Int) {
        manager.cancel(notificationId)
    }
}
