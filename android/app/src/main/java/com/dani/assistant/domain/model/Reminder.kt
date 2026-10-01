package com.dani.assistant.domain.model

enum class ReminderType(val arabicTitle: String) {
    ALARM_CLOCK("منبه"),      // AlarmManager.setAlarmClock (Ringing, wakes screen)
    NOTIFICATION("إشعار")     // AlarmManager.setExactAndAllowWhileIdle (Heads-up notification)
}

data class Reminder(
    val id: Long = 0,
    val taskId: Long,
    val title: String,
    val triggerTime: Long, // Epoch ms
    val reminderType: ReminderType = ReminderType.NOTIFICATION,
    val isActive: Boolean = true
)
