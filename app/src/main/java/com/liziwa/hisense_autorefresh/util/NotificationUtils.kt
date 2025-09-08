package com.liziwa.hisense_autorefresh.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.liziwa.hisense_autorefresh.R
import com.liziwa.hisense_autorefresh.activity.MainActivity

class NotificationUtils {

    companion object : SingletonHolder<NotificationUtils, Context>(::NotificationUtils)

    private var context: Context
    private var intent: Intent
    private var pendingIntent: PendingIntent
    private var manager: NotificationManager

    private val NOTIFICATION_ERROR_ID = 1001
    val NOTIFICATION_ID = 1002
    private var showErrorNotification = false

    private constructor(context: Context) {
        this.context = context
        intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager = context.getSystemService(NotificationManager::class.java)
    }

    private val CHANNEL_ERROR_ID = "service_error_channel"
    private val CHANNEL_ID = "service_channel"

    // 创建通知渠道
    fun createErrorNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel =
            NotificationChannel(CHANNEL_ERROR_ID, context.getString(R.string.notification_channel_name), importance).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }
        manager.createNotificationChannel(channel)
    }

    // 创建完全静默的通知渠道
    fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), importance).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false) // 不在应用图标上显示角标
                // 完全静默设置
                enableVibration(false) // 禁用震动
                enableLights(false)    // 禁用指示灯
                setSound(null, null)   // 禁用声音
            }
        manager.createNotificationChannel(channel)
    }

    // 显示服务失败通知
    fun showServiceFailedNotification() {
        // 创建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ERROR_ID)
            .setContentTitle(context.getString(R.string.notification_title_error))
            .setContentText(context.getString(R.string.notification_desc_error))
            .setSmallIcon(R.drawable.icon_small) // 自定义错误图标
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // 设置点击通知后的跳转
            .setVibrate(longArrayOf(0, 500, 250, 500)) // 震动模式
            .setCategory(Notification.CATEGORY_ERROR)
            .build()

        // 显示通知
        manager.notify(NOTIFICATION_ERROR_ID, notification)
        showErrorNotification = true
    }

    fun removeErrorNotification() {
        if (showErrorNotification) {
            manager.cancel(NOTIFICATION_ERROR_ID)
            manager.deleteNotificationChannel(CHANNEL_ERROR_ID)
            showErrorNotification = false
        }
    }

    fun showNotification(text: String, isUpdate: Boolean): Notification {
        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle(context.getString(R.string.notification_title)) // 通知标题
            setContentText(text) // 通知内容
            setSmallIcon(R.drawable.icon)
            setContentIntent(pendingIntent) // 设置点击行为
            priority = NotificationCompat.PRIORITY_LOW
            setCategory(Notification.CATEGORY_SERVICE)
            setOngoing(true) // 设置为持续通知（不可滑动消除）
            setAutoCancel(false) // 禁止自动取消
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
            setOnlyAlertOnce(true)
        }.build()
        if (isUpdate) {
            manager.notify(NOTIFICATION_ID, notification)
        }
        return notification
    }
}