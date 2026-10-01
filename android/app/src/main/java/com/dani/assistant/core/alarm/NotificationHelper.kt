package com.dani.assistant.core.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dani.assistant.MainActivity
import com.dani.assistant.R

object NotificationHelper {

    const val CHANNEL_CRITICAL_ALARMS = "channel_critical_alarms"
    const val CHANNEL_DAILY_TASKS = "channel_daily_tasks"
    const val CHANNEL_REMINDERS = "channel_reminders"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Critical Alarms Channel (High Importance, Ringing, Vibration)
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALARMS,
                "المنبهات الحرجة (Critical Alarms)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "منبهات المواعيد والمهام العاجلة التي تتطلب انتباهاً فورياً"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }

            // 2. Daily Tasks Channel (Default Importance)
            val tasksChannel = NotificationChannel(
                CHANNEL_DAILY_TASKS,
                "إشعارات المهام اليومية (Daily Tasks)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات المهام والأنشطة المجدولة خلال اليوم"
                enableVibration(true)
            }

            // 3. Low Importance Reminders Channel
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "تذكيرات هادئة (Gentle Reminders)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تذكيرات روتينية خفيفة دون إصدار أصوات مزعجة"
            }

            notificationManager.createNotificationChannels(
                listOf(criticalChannel, tasksChannel, remindersChannel)
            )
        }
    }

    fun showReminderNotification(
        context: Context,
        reminderId: Long,
        taskId: Long,
        title: String,
        isAlarmClock: Boolean
    ) {
        val channelId = if (isAlarmClock) CHANNEL_CRITICAL_ALARMS else CHANNEL_DAILY_TASKS

        // Tap content intent (opens app)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: "تم الإنجاز" (Marks task as completed)
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_DONE
            putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + 200000).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: "تأجيل 30 دقيقة" (Snoozes reminder +30 minutes)
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_30
            putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_IS_ALARM, isAlarmClock)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + 300000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isAlarmClock) "⏰ منبه DANI: $title" else "🔔 تذكير مهمة: $title")
            .setContentText("حان وقت تنفيذ المهمة المجدولة")
            .setPriority(if (isAlarmClock) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isAlarmClock) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "تم الإنجاز",
                donePendingIntent
            )
            .addAction(
                android.R.drawable.ic_popup_sync,
                "تأجيل 30 دقيقة",
                snoozePendingIntent
            )

        if (isAlarmClock) {
            builder.setFullScreenIntent(contentPendingIntent, true)
            builder.setOngoing(true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(reminderId.toInt(), builder.build())
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS is not yet granted
        }
    }

    fun dismissNotification(context: Context, reminderId: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(reminderId.toInt())
    }
}
