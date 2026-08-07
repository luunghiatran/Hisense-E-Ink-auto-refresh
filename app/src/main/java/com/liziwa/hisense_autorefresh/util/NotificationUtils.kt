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

/**
 * 前台服务通知管理：
 * - 常驻“已监控”通知（CHANNEL_ID，静默、不可消除）
 * - 服务异常时弹出“错误”通知（CHANNEL_ERROR_ID，震动提醒）
 */
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

    /** 错误通知渠道：高优先级 + 震动，用于服务启动失败/权限异常时提醒用户 */
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

    /** 正常服务通知渠道：完全静默（无震动/灯/声），用于常驻“已监控”通知 */
    fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
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

    /** 弹出服务失败/异常通知（高优先级、震动、点击跳转回应用） */
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

    /** 移除并删除错误通知渠道（进入前台/恢复正常时调用） */
    fun removeErrorNotification() {
        if (showErrorNotification) {
            manager.cancel(NOTIFICATION_ERROR_ID)
            manager.deleteNotificationChannel(CHANNEL_ERROR_ID)
            showErrorNotification = false
        }
    }

    /**
     * 构建并返回常驻“已监控”通知。
     * @param text    通知内容（通常为当前计数/状态）
     * @param isUpdate 仅当为 true 时才 notify 更新；返回的通知用于 startForeground 首次展示
     */
    fun showNotification(text: String, isUpdate: Boolean): Notification {
        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle(context.getString(R.string.notification_title)) // 通知标题
            setContentText(text) // 通知内容
            setSmallIcon(R.drawable.icon)
            setContentIntent(pendingIntent) // 设置点击行为
            priority = NotificationCompat.PRIORITY_DEFAULT
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