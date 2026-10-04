package com.dani.assistant.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.digest.MorningDigest
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val pendingResult = goAsync()
            try { MorningDigest.schedule(context) } catch (e: Exception) { }
            try { com.dani.assistant.core.meds.MedAlarms.rescheduleAll(context) } catch (e: Exception) { }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val now = System.currentTimeMillis()
                    val reminderDao = DaniApplication.instance.database.reminderDao()
                    val scheduler = DaniApplication.instance.alarmScheduler

                    // Read active future reminders from Room
                    val futureReminders = reminderDao.getActiveFutureReminders(now)

                    futureReminders.forEach { entity ->
                        val reminder = Reminder(
                            id = entity.id,
                            taskId = entity.taskId,
                            title = entity.title,
                            triggerTime = entity.triggerTime,
                            reminderType = try {
                                ReminderType.valueOf(entity.reminderType)
                            } catch (e: Exception) {
                                ReminderType.NOTIFICATION
                            },
                            isActive = entity.isActive
                        )
                        // Re-register each reminder with AlarmManager
                        scheduler.schedule(reminder)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
