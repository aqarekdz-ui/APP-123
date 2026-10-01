package com.dani.assistant.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dani.assistant.MainActivity
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType

sealed class ScheduleResult {
    object Success : ScheduleResult()
    object ExactAlarmPermissionRequired : ScheduleResult()
    data class Error(val message: String) : ScheduleResult()
}

interface AlarmScheduler {
    fun schedule(reminder: Reminder): ScheduleResult
    fun cancel(reminderId: Long)
    fun canScheduleExactAlarms(): Boolean
}

class AndroidAlarmScheduler(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    override fun schedule(reminder: Reminder): ScheduleResult {
        if (alarmManager == null) {
            return ScheduleResult.Error("خدمة المنبه غير متوفرة في النظام")
        }

        // Android 12+ (API 31+) permission check for exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return ScheduleResult.ExactAlarmPermissionRequired
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmReceiver.EXTRA_TASK_ID, reminder.taskId)
            putExtra(AlarmReceiver.EXTRA_TITLE, reminder.title)
            putExtra(AlarmReceiver.EXTRA_TYPE, reminder.reminderType.name)
        }

        // Unique pending intent per reminder ID using FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            when (reminder.reminderType) {
                ReminderType.ALARM_CLOCK -> {
                    // Critical alarm: wakes screen, rings until dismissed
                    val showIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val showPendingIntent = PendingIntent.getActivity(
                        context,
                        (reminder.id + 100000).toInt(),
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(reminder.triggerTime, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                }

                ReminderType.NOTIFICATION -> {
                    // Exact notification: fires at exact minute even in Doze Mode
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTime,
                        pendingIntent
                    )
                }
            }
            return ScheduleResult.Success
        } catch (e: SecurityException) {
            return ScheduleResult.ExactAlarmPermissionRequired
        } catch (e: Exception) {
            return ScheduleResult.Error(e.localizedMessage ?: "حدث خطأ أثناء جدولة التنبيه")
        }
    }

    override fun cancel(reminderId: Long) {
        if (alarmManager == null) return

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
