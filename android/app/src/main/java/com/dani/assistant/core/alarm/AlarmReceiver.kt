package com.dani.assistant.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dani.assistant.DaniApplication
import com.dani.assistant.domain.model.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "تنبيه مهمة"
        val typeStr = intent.getStringExtra(EXTRA_TYPE) ?: ReminderType.NOTIFICATION.name
        val isAlarmClock = typeStr == ReminderType.ALARM_CLOCK.name

        if (reminderId != -1L) {
            // Show notification
            NotificationHelper.showReminderNotification(
                context = context,
                reminderId = reminderId,
                taskId = taskId,
                title = title,
                isAlarmClock = isAlarmClock
            )

            // Mark reminder as inactive in database so it doesn't re-trigger
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DaniApplication.instance.database.reminderDao().deactivateReminder(reminderId)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TYPE = "extra_type"
    }
}
