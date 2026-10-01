package com.dani.assistant.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dani.assistant.DaniApplication
import com.dani.assistant.data.local.entity.ReminderEntity
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)

        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_MARK_DONE -> {
                if (taskId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val task = DaniApplication.instance.taskRepository.getTaskByIdSync(taskId)
                            if (task != null) {
                                DaniApplication.instance.taskRepository.toggleTaskCompleted(task)
                            }
                            NotificationHelper.dismissNotification(context, reminderId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    pendingResult.finish()
                }
            }

            ACTION_SNOOZE_30 -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "تذكير مهمة"
                val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
                val newTriggerTime = System.currentTimeMillis() + (30 * 60 * 1000L) // +30 minutes

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminderDao = DaniApplication.instance.database.reminderDao()
                        // Insert new or update snoozed reminder in Room
                        val newReminderId = reminderDao.insertReminder(
                            ReminderEntity(
                                taskId = taskId,
                                title = title,
                                triggerTime = newTriggerTime,
                                reminderType = if (isAlarm) ReminderType.ALARM_CLOCK.name else ReminderType.NOTIFICATION.name,
                                isActive = true
                            )
                        )

                        // Schedule the new alarm
                        val scheduler = DaniApplication.instance.alarmScheduler
                        scheduler.schedule(
                            Reminder(
                                id = newReminderId,
                                taskId = taskId,
                                title = title,
                                triggerTime = newTriggerTime,
                                reminderType = if (isAlarm) ReminderType.ALARM_CLOCK else ReminderType.NOTIFICATION,
                                isActive = true
                            )
                        )

                        NotificationHelper.dismissNotification(context, reminderId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.dani.assistant.ACTION_MARK_DONE"
        const val ACTION_SNOOZE_30 = "com.dani.assistant.ACTION_SNOOZE_30"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_ALARM = "extra_is_alarm"
    }
}
