package com.dani.assistant

import android.app.Application
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.core.alarm.AndroidAlarmScheduler
import com.dani.assistant.core.alarm.NotificationHelper
import com.dani.assistant.core.digest.MorningDigest
import com.dani.assistant.core.database.DaniDatabase
import com.dani.assistant.data.repository.TaskRepository
import com.dani.assistant.data.repository.TaskRepositoryImpl

class DaniApplication : Application() {

    val database: DaniDatabase by lazy {
        DaniDatabase.getDatabase(this)
    }

    val alarmScheduler: AlarmScheduler by lazy {
        AndroidAlarmScheduler(this)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepositoryImpl(
            taskDao = database.taskDao(),
            reminderDao = database.reminderDao(),
            alarmScheduler = alarmScheduler
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.dani.assistant.core.debug.CrashLog.install(this)

        // Initialize notification channels at application startup
        try { NotificationHelper.createNotificationChannels(this) } catch (e: Exception) { }

        // الملخص الصباحي اليومي
        try { MorningDigest.schedule(this) } catch (e: Exception) { }

        // نسخة احتياطية أسبوعية تلقائية (WorkManager)
        try { com.dani.assistant.core.backup.AutoBackup.schedule(this) } catch (e: Exception) { }

        // مراجعة أسبوعية (أحد 19:00)
        try { com.dani.assistant.core.digest.WeeklyReview.schedule(this) } catch (e: Exception) { }

        // تقرير الشهر الفايت (يوم 1)
        try { com.dani.assistant.core.digest.MonthlyReport.schedule(this) } catch (e: Exception) { }

        // تذكير العادات المسائي (21:00)
        try { com.dani.assistant.core.habits.HabitReminder.schedule(this) } catch (e: Exception) { }

        // مصاريف ثابتة شهرية (تسجيل تلقائي)
        try { com.dani.assistant.core.money.RecurringExpenses.schedule(this) } catch (e: Exception) { }
        Thread {
            try { com.dani.assistant.core.money.RecurringExpenses.runAndNotify(this) } catch (e: Exception) { }
            try { com.dani.assistant.core.meds.MedAlarms.rescheduleAll(this) } catch (e: Exception) { }
            try { com.dani.assistant.core.events.EventAlarms.rescheduleAll(this) } catch (e: Exception) { }
        }.start()

        // مراقب الإعلانات (WorkManager)
        try { com.dani.assistant.core.watch.AdWatcher.reschedule(this) } catch (e: Exception) { }
    }

    companion object {
        lateinit var instance: DaniApplication
            private set
    }
}
